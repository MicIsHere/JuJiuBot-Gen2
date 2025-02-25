package cn.cutemic.bot.util

import cn.cutemic.bot.Bot
import cn.cutemic.bot.database.*
import cn.cutemic.bot.model.MessageExposed
import cn.cutemic.bot.model.legacy.LegacyMessageData
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

    private val list = mutableListOf<MessageExposed>()
    private val groupCache = mutableMapOf<Long,String>()

    private fun processMessage(message: LegacyMessageData) {
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
        }

        keywords.clear()
        keywords.append(message.rawMessage)

        list.add(MessageExposed(null, group, message.userID.toLong(), message.rawMessage, keywords.toString(), plainText, "${message.time}000".toLong(), "516b191e-d250-42ab-b08e-3ff8d073397e"))
    }

    fun transform(database: MongoDatabase){
        // message 处理
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

            Bot.LOGGER.info("${list.size} need add, posting request...")
            messageService.addMany(list, false, 100000)
        }
    }

}