package run.mic.bot.util

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.koin.java.KoinJavaComponent.inject
import run.mic.bot.Trace
import run.mic.bot.database.*
import run.mic.bot.model.MessageExposed
import run.mic.bot.model.context.AnswerEntry
import run.mic.bot.model.context.ContextEntry
import run.mic.bot.model.fast.FastContextEntry
import run.mic.bot.model.fast.FastMessageExposed
import run.mic.bot.model.legacy.LegacyMessageData
import run.mic.bot.model.legacy.context.LegacyContext
import run.mic.bot.model.legacy.context.fast.FastLegacyContext
import java.util.*
import kotlin.system.exitProcess

object LegacyDatabaseMove {

    private val groupService by inject<GroupService>(GroupService::class.java)
    private val botService by inject<BotService>(BotService::class.java)
    private val contextService by inject<ContextService>(ContextService::class.java)
    private val answerService by inject<AnswerService>(AnswerService::class.java)
    private val messageService by inject<MessageService>(MessageService::class.java)

    private val messageList = mutableListOf<MessageExposed>()
    private val groupCache = mutableMapOf<Long, String>()
    private val contextList = mutableListOf<ContextEntry>()
    private val answerList = mutableListOf<AnswerEntry>()

    fun transform(database: MongoDatabase) {
        runBlocking {
            groupService.readAll().forEach {
                groupCache[it.group] = it.id!!
            }
        }

//        message(database)
//        contextMessage(database)
//        context(database)
        answer(database)
    }

    private fun processMessage(message: LegacyMessageData) {
        Trace.info("Processing message...")
        val group = groupCache[message.groupID.toLong()]
        var plainText: String? = null

        val keywords = StringBuilder()

        if (group == null) {
            Trace.info("Cannot get group(${message.groupID}) id, ignore.")
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

        messageList.add(
            MessageExposed(
                null,
                group,
                message.userID.toLong(),
                message.rawMessage,
                keywords.toString(),
                plainText,
                "${message.time}000".toLong(),
                "0041cafe-210b-4d38-ba48-19674dbb74aa"
            )
        )
    }

    private fun message(database: MongoDatabase) {
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

            Trace.info("${messageList.size} message(s) need add, posting request...")
            messageService.addMany(messageList, false, 100000)
        }
    }

    private fun context(database: MongoDatabase) {
        runBlocking {
            val contexts = database.getCollection<LegacyContext>("context").find().toList()
            // 并发处理所有消息
            val jobs = contexts.map { context ->
                async {
                    processContext(context)
                }
            }
            jobs.awaitAll()

            Trace.info("${contexts.size} context(s) need add, posting request...")
            contextService.addMany(contextList, false, 10000)
        }
    }

    private fun processContext(context: LegacyContext) {
        Trace.info("Processing context(${context.id})...")
        val keywordList = context.keywords.split(" ")
        val keywords = keywordList.joinToString("|")
        val weights = List(keywordList.size) { "1.0" }.joinToString("|")

        contextList.add(
            ContextEntry(
                null,
                keywords,
                weights,
                context.count,
                ("${context.clearTime}000".toLongOrNull() ?: "${context.time}000".toLong()),
                context.id.toString()
            )
        )
    }

    private fun contextMessage(database: MongoDatabase) {
        runBlocking {
            val contexts = database.getCollection<LegacyContext>("context").find().toList()
            val messages = messageService.fastReadAll()
            // 并发处理所有消息
            Trace.info("Loaded ${messages.size}[${contexts.size} Async] message, added to cache")
            val jobs = contexts.map { context ->
                async {
                    processContextMessage(context, messages)
                }
            }
            jobs.awaitAll()

            Trace.info("${contexts.size} context-message(s) need add, posting request...")
            messageService.addMany(messageList, false, 10000)
            exitProcess(0)
        }
    }

    private fun processContextMessage(context: LegacyContext, allMessage: List<FastMessageExposed>) {
        context.answers.forEach { answer ->
            val groupID = groupCache[answer.groupID] ?: return
            answer.messages.forEach { message ->
                val message1 = allMessage
                    .asSequence()
                    .filter { it.groupID.equals(groupID) && it.rawMessage.equals(message) }
                    .singleOrNull { it.time.equals("${answer.time}000".toLong()) }

                if (message1 == null) {
                    val keywordList = context.keywords.split(" ")
                    val keywords = keywordList.joinToString("|")
                    messageList.add(
                        MessageExposed(
                            null,
                            groupCache[answer.groupID]!!,
                            null,
                            message,
                            keywords,
                            message,
                            "${answer.time}000".toLong(),
                            "0041cafe-210b-4d38-ba48-19674dbb74aa"
                        )
                    )
                    Trace.info("Added message by context...")
                }
            }
        }
    }

    private fun answer(database: MongoDatabase) {
        val jobs = mutableListOf<Job>()
        runBlocking {
            val fastLegacyContexts = mutableListOf<FastLegacyContext>()
            database.getCollection<LegacyContext>("context").find().toList().forEach {
                fastLegacyContexts.add(
                    FastLegacyContext(
                        it.id.toString(),
                        it.keywords,
                        it.time,
                        it.count,
                        it.answers,
                        it.clearTime,
                        it.ban
                    )
                )
            }
            Trace.info("Loaded ${fastLegacyContexts.size} context(fast), added to cache")

            val messages = messageService.fastReadAll()
            val newContexts = contextService.fastReadAll()

            // 并发处理所有消息
            Trace.info("Loaded ${messages.size} message, added to cache")
            Trace.info("Loaded ${newContexts.size}[${fastLegacyContexts.size} Async] new-context, added to cache")
            fastLegacyContexts.map { context ->
                val job = launch(Dispatchers.Default) {
                    processAnswer(context, messages, newContexts)
                }
                jobs.add(job)
            }
            jobs.joinAll()

            Trace.info("${fastLegacyContexts.size} answer need add, posting request...")
            if (messageList.isNotEmpty()) {
                messageService.addMany(messageList, false, 10000)
            }
            answerService.addMany(answerList, false, 10000)
            exitProcess(0)
        }
    }

    private fun processAnswer(
        context: FastLegacyContext,
        allMessage: List<FastMessageExposed>,
        allNewContext: List<FastContextEntry>
    ) {
        Trace.info("Processing context(${context.id})...")
        context.answers.forEach { answer ->
            val groupID = groupCache[answer.groupID]
            if (Objects.equals(groupID, null)) {
                return@forEach
            }
            answer.messages.forEach { message ->
                val message1 = allMessage.lastOrNull {
                    Objects.equals(it.groupID, groupID) && Objects.equals(
                        it.rawMessage,
                        message
                    )
                }
                val allNewContext1 =
                    allNewContext.singleOrNull { Objects.equals(it.legacyID.toString(), context.id.toString()) }
                        ?: throw NullPointerException("Cannot find context: ${context.id}")
                var nowID: String? = null

                if (Objects.equals(message1, null)) {
                    nowID = UUID.randomUUID().toString()
                    messageList.add(
                        MessageExposed(
                            nowID,
                            groupID!!,
                            null,
                            message,
                            context.keywords,
                            message,
                            "${answer.time}000".toLong(),
                            "0041cafe-210b-4d38-ba48-19674dbb74aa"

                        )
                    )
                    Trace.info("Cannot find message: $message($groupID)[${answer.time}], ready add $nowID")
                }

                answerList.add(
                    AnswerEntry(
                        null,
                        groupCache[answer.groupID]!!,
                        answer.count,
                        allNewContext1.id!!,
                        message1?.id ?: nowID!!,
                        "${answer.time}000".toLong()
                    )
                )
            }

        }

    }

}