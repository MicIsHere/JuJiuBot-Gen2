package cn.cutemic.bot.module.impl.chat

import cn.cutemic.bot.Bot
import cn.cutemic.bot.database.*
import cn.cutemic.bot.model.IgnoreCommand
import cn.cutemic.bot.model.MessageExposed
import cn.cutemic.bot.model.context.AnswerEntry
import cn.cutemic.bot.model.context.ContextEntry
import cn.cutemic.bot.module.BotModule
import love.forte.simbot.common.id.toLong
import love.forte.simbot.component.onebot.common.annotations.InternalOneBotAPI
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.utils.sendGroupTextMsgApi
import love.forte.simbot.event.EventResult
import org.koin.java.KoinJavaComponent.inject


@OptIn(InternalOneBotAPI::class)
object Chat: BotModule("聊天","与牛牛聊天") {

    private val groupService by inject<GroupService>(GroupService::class.java)
    private val botService by inject<BotService>(BotService::class.java)
    private val contextService by inject<ContextService>(ContextService::class.java)
    private val answerService by inject<AnswerService>(AnswerService::class.java)
    private val messageService by inject<MessageService>(MessageService::class.java)

    // 学习关键词的数量
    private const val KEYWORD_SIZE = 2

    // 维护一个消息列表
    private val messageID = mutableListOf<String>()

    init {
        event {
            on<OneBotNormalGroupMessageEvent> {
                val message = messageContent.plainText ?: ""
                val bot = botService.read(bot.userId.toLong()) ?: throw NullPointerException("Cannot get bot-id in database.")
                val group = groupService.read(groupId.toLong()) ?: throw NullPointerException("Cannot get group-id in database.")

                if (message.length <= 2) {
                    Bot.LOGGER.info("Message($message) too short or not text, ignore.")
                    return@on EventResult.invalid()
                }

                if (rawMessage.startsWith("[CQ:")) {
                    Bot.LOGGER.error("Message have CQ-Code, ignore.")
                    return@on EventResult.invalid()
                }

                if (IgnoreCommand.equals(message)) {
                    Bot.LOGGER.info("Message($message) is command, ignore.")
                    return@on EventResult.invalid()
                }

                val chatData = MessageExposed(
                    null,
                    group,
                    userId.value,
                    rawMessage,
                    analyzeText(message),
                    message,
                    time.milliseconds,
                    bot
                )

                reply(chatData).let {
                    if (it != null) {
                        Bot.LOGGER.info("Send reply message: $it")
                        Bot.ONEBOT.executeData(sendGroupTextMsgApi(groupId, it))
                    }
                }

                learn(chatData)
                EventResult.invalid()
            }
        }
    }

    private suspend fun learn(data: MessageExposed){
        // 获取群里的上一条发言 当作现在处理这条消息的(问题)
        if (messageID.isEmpty()) {
            Bot.LOGGER.info("Context is empty, ignore.")
            messageService.add(data).let {
                messageID.add(it)
            }
            return
        }

        val lastMessage = messageService.read(messageID.last()) ?: throw NullPointerException("Cannot get last message in database.")
        // 添加这次发言的信息数据
        messageService.add(data).let {
            messageID.add(it)
        }
        // 复读检查
        if (lastMessage.plainText == data.plainText) {
            return
        }
        // 上一条发言用户不是这次数据的用户 将尝试获取前三条数据
        if (lastMessage.userID != data.userID) {
            runCatching {
                messageService.readListByGroupID(data.groupID)
                    .asReversed()
                    .take(2)
                    .firstOrNull { msg ->
                        requireNotNull(msg.userID) { "Invalid user ID in message" }
                        msg.userID == lastMessage.userID && msg.plainText != lastMessage.plainText // 复读检查
                    }?.let { foundMsg ->
                        val foundMessageKeyword = analyzeText(foundMsg.plainText!!)
                        val contextID = insectContext(foundMessageKeyword)
                        answerService.add(AnswerEntry(
                            data.groupID,
                            1,
                            contextID,
                            lastMessage.id!!,
                            System.currentTimeMillis()
                        ))
                    }
            }.onFailure {
                Bot.LOGGER.error("Error processing messages: ${it.message}")
                throw it
            }
        }

        insectContext(analyzeText(lastMessage.plainText!!)).let {
            answerService.add(AnswerEntry(
                data.groupID,
                1,
                it,
                lastMessage.id!!,
                System.currentTimeMillis()
            ))
        }
    }

    private fun analyzeText(text: String): String{
        val keywordList = Bot.TFIDF.analyze(text, KEYWORD_SIZE)
        val result = StringBuilder()

        if (keywordList.isEmpty()) {
            Bot.LOGGER.warn("TD-IDF Analyze is failed, trying use HanLp API...")
            runCatching {
                val list = Bot.HAN_LP.keyphraseExtraction(text, KEYWORD_SIZE)
                // HanLP 也可能分析不出数据
                if (list.isEmpty()) {
                    throw NullPointerException("HanLp Analyze is failed")
                }
                list.forEach {
                    result.append("${it.key} ")
                }
                return result.toString()
            }.onFailure {
                // HanLP分析失败 使用原文本当作上下文
                Bot.LOGGER.error("HanLp Analyze is failed, use raw-text...")
                result.append(text)
            }
        } else {
            keywordList.forEach {
                result.append("${it.name} ")
            }
        }
        return result.toString()
    }

    private suspend fun reply(data: MessageExposed): String?{
        Bot.LOGGER.info("Trying reply...")
        if (data.plainText == null) {
            return null
        }

        if (data.plainText != "" && data.plainText.length < 2) {
            return null
        }

        val contextId = contextService.getId(data.keywords) ?: return null
        val answerList = answerService.getAnswerByContextId(contextId)
        if (answerList.isEmpty()) {
            return null
        }

        val messageID = answerList
            .asReversed()
            .minByOrNull { it.count }
            ?.message

        if (messageID == null) {
            return null
        }

        return messageService.read(messageID)?.rawMessage
    }

    private suspend fun insectContext(keyword: String): String{
        var contextID = contextService.getId(keyword)
        if (contextID == null) {
            contextID = contextService.add(ContextEntry(
                keywords = keyword,
                count = 1,
                lastUpdated = System.currentTimeMillis()
            ))
            return contextID
        }
        Bot.LOGGER.info("Update context count.")
        val count = contextService.get(contextID)!!.count++
        contextService.update(contextID, ContextEntry(
            keywords = keyword,
            count = count,
            lastUpdated = System.currentTimeMillis()
        ))
        return contextID
    }

}