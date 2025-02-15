package cn.cutemic.bot.module.impl.chat

import cn.cutemic.bot.Bot
import cn.cutemic.bot.database.BotService
import cn.cutemic.bot.database.GroupService
import cn.cutemic.bot.model.IgnoreCommand
import cn.cutemic.bot.model.MessageExposed
import cn.cutemic.bot.module.BotModule
import cn.cutemic.bot.util.Task
import kotlinx.coroutines.runBlocking
import love.forte.simbot.common.collectable.toList
import love.forte.simbot.common.id.toLong
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.event.EventResult
import org.koin.java.KoinJavaComponent.inject


object Chat: BotModule("聊天","与牛牛聊天") {

    private val groupService by inject<GroupService>(GroupService::class.java)
    private val botService by inject<BotService>(BotService::class.java)

    // 学习关键词的数量
    private const val KEYWORD_SIZE = 2

    val messageCache = MessageCache()
    val contextCache = ContextCache()

    init {
        Bot.ONEBOT.groupRelation.groups.toList().forEach {
            runBlocking {
                val group = groupService.read(it.id.toLong()) ?: throw NullPointerException("Cannot get group-id in database.")
                Bot.LOGGER.info("Init group(${it.id}) last sync time.")
                messageCache.updateLastSyncTime(group)
            }
        }

        event {
            on<OneBotNormalGroupMessageEvent> {
                val message = messageContent.plainText ?: ""
                val bot = botService.read(bot.userId.toLong()) ?: throw NullPointerException("Cannot get bot-id in database.")
                val group = groupService.read(groupId.toLong()) ?: throw NullPointerException("Cannot get group-id in database.")

                if (message.length <= 2) {
                    Bot.LOGGER.info("Message($message) too short or not text, ignore.")
                    return@on EventResult.invalid()
                }

                if (IgnoreCommand.equals(message)) {
                    Bot.LOGGER.info("Message($message) is command, ignore.")
                    return@on EventResult.invalid()
                }


                val chatData = MessageExposed(
                    group,
                    userId.value,
                    rawMessage,
                    analyzeText(message),
                    message,
                    time.milliseconds,
                    bot
                )

                learn(chatData)
                EventResult.invalid()
            }
        }
    }

    private fun learn(data: MessageExposed){
        // 获取群里的上一条发言 当作现在处理这条消息的(问题)
        val lastMessage = messageCache.get(data.groupID).let {
            if (it.isEmpty()) {
                Bot.LOGGER.info("Context is empty, ignore.")
                messageCache.addMessage(data)
                return
            }
            it.last()
        }
        // 添加这次发言的信息数据
        messageCache.addMessage(data)

        // 复读检查
        if (lastMessage.plainText == data.plainText) {
            return
        }
        // 上一条发言用户不是这次数据的用户 将尝试获取前三条数据
        if (lastMessage.userID != data.userID) {
            runCatching {
                messageCache.get(data.groupID).asReversed()
                    .take(2)
                    .firstOrNull { msg ->
                        requireNotNull(msg.userID) { "Invalid user ID in message" }
                        msg.userID == lastMessage.userID &&
                        msg.plainText != lastMessage.plainText // 复读检查
                    }?.let { foundMsg ->
                        contextCache.upsert(
                            analyzeText(foundMsg.plainText!!),
                            analyzeText(data.plainText!!),
                            data.groupID,
                            data.plainText
                        )
                    }
            }.onFailure {
                Bot.LOGGER.error("Error processing messages: ${it.message}")
            }
        }

        contextCache.upsert(
            analyzeText(lastMessage.plainText!!),
            analyzeText(data.plainText!!),
            data.groupID,
            data.plainText
        )
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

    private fun reply(data: MessageExposed){
        if (data.plainText != "" && data.plainText!!.length < 2) {
            return
        }
    }

    @Task(15L)
    private fun autoSync(){
        Bot.LOGGER.info("Trying auto-sync...")
        messageCache.messageSyncToDatabase()
        contextCache.sync()
    }

}