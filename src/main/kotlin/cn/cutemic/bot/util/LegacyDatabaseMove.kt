package cn.cutemic.bot.util

import cn.cutemic.bot.Bot
import cn.cutemic.bot.database.*
import cn.cutemic.bot.model.MessageExposed
import cn.cutemic.bot.model.context.AnswerEntry
import cn.cutemic.bot.model.context.ContextEntry
import cn.cutemic.bot.model.legacy.LegacyMessageData
import cn.cutemic.bot.model.legacy.context.LegacyContext
import cn.cutemic.bot.module.impl.Chat
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject

object LegacyDatabaseMove {

    private val groupService by inject<GroupService>(GroupService::class.java)
    private val botService by inject<BotService>(BotService::class.java)
    private val contextService by inject<ContextService>(ContextService::class.java)
    private val answerService by inject<AnswerService>(AnswerService::class.java)
    private val messageService by inject<MessageService>(MessageService::class.java)

    private val messageList = mutableListOf<MessageExposed>()
    private val groupCache = mutableMapOf<Long,String>()

    private fun processMessage(message: LegacyMessageData) {
        Bot.LOGGER.info("Processing message...")
        val group = groupCache[message.groupID.toLong()]
        var plainText: String? = null

        val keywords = StringBuilder()

        if (group == null) {
            Bot.LOGGER.info("Cannot get group(${message.groupID}) id, ignore.")
            return
        }

        if (message.isPlainText.toBoolean()) {
            plainText = message.plainText
        }

        val lists = message.keywords.split(" ")
        var count = 1

        if (!message.rawMessage.startsWith("[CQ:") && !message.rawMessage.endsWith("]")) {
            lists.forEach {
                if (count == lists.size) {
                    keywords.append(it)
                    count = 1
                } else {
                    keywords.append("$it|")
                    count++
                }
            }
        } else {
            keywords.clear()
            keywords.append(message.rawMessage)
        }

        messageList.add(MessageExposed(null, group, message.userID.toLong(), message.rawMessage, keywords.toString(), plainText, "${message.time}000".toLong(), "516b191e-d250-42ab-b08e-3ff8d073397e"))
    }

    fun transform(database: MongoDatabase){
        runBlocking {
            groupService.readAll().forEach {
                groupCache[it.group] = it.id!!
            }
        }
        context(database)
    }

    private fun message(database: MongoDatabase){
        runBlocking {
            groupService.readAll().forEach {
                groupCache[it.group] = it.id!!
            }

            val messages = database.getCollection<LegacyMessageData>("message").find().toList()

            // 并发处理所有消息
            val jobs = messages.map { message ->
                async {
                    processMessage(message)
                }
            }
            jobs.awaitAll()

            Bot.LOGGER.info("${messageList.size} need add, posting request...")
            messageService.addMany(messageList, false, 100000)
        }
    }

    private fun context(database: MongoDatabase){
        Bot.LOGGER.info("Processing context...")
        var count = 0
        runBlocking {
            val contexts = database.getCollection<LegacyContext>("context").find().toList()
            Bot.LOGGER.info("${contexts.size} need add, posting request...")
            // 并发处理所有消息
            val jobs = contexts.map { context ->
                println("Success $count/${contexts.size}")
                processContext(context)
                count++
            }
        }
    }

    private fun processContext(context: LegacyContext){
        runBlocking {
            val lists = context.keywords.split(" ")
            var count = 1
            val keywords = StringBuilder()
            val weight = StringBuilder()

            lists.forEach {
                if (count == lists.size) {
                    keywords.append(it)
                    weight.append("1.0")
                    count = 1
                } else {
                    keywords.append("$it|")
                    weight.append("1.0|")
                    count++
                }
            }

            val contextID = contextService.add(ContextEntry(
                keywords.toString(),
                weight.toString(),
                context.count,
                ("${context.clearTime}000".toLongOrNull() ?: "${context.time}000".toLong())
            ))

            Bot.LOGGER.info("Context added: $contextID")

            context.answers.forEach { answer ->
                answer.messages.forEach { messages ->
                    var id: MessageExposed? = messageService.searchMessageOnLegacyDatabaseTransform(
                        groupCache[answer.groupID]?: return@runBlocking,
                        messages,
                        "${answer.time}000".toLong())

                    if (id == null) {
                        Bot.LOGGER.info("Cannot find message, new!")
                        id = messageService.read(
                            messageService.add(MessageExposed(
                                null,
                                groupCache[answer.groupID]!!,
                                null,
                                messages,
                                keywords.toString(),
                                messages,
                                "${answer.time}000".toLong(),
                                "516b191e-d250-42ab-b08e-3ff8d073397e"
                            ))
                        )!!
                    }

                    val allAnswer = answerService.getAnswerByContextId(contextID)
                        .filter { it.group != id.groupID }
                        .filter { it.message == id.id }

                    if (allAnswer.size >= 2) {
                        Bot.LOGGER.info("Add anything message!")
                        allAnswer.forEach { answerService.delete(it.id!!) }
                        answerService.add(AnswerEntry(
                            null,
                            null,
                            answer.count,
                            contextID,
                            id.id!!,
                            "${answer.time}000".toLong(),
                        ))
                        return@runBlocking
                    }

                    answerService.add(AnswerEntry(
                        null,
                        groupCache[answer.groupID]!!,
                        answer.count,
                        contextID,
                        id.id!!,
                        "${answer.time}000".toLong(),
                    ))
                }
            }
        }

    }

}