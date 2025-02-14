package cn.cutemic.bot.module.impl.chat

import cn.cutemic.bot.Bot
import cn.cutemic.bot.data.ChatData
import cn.cutemic.bot.data.IgnoreCommand
import cn.cutemic.bot.module.BotModule
import cn.cutemic.bot.util.Task
import love.forte.simbot.common.collectable.toList
import love.forte.simbot.common.id.toLong
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.event.EventResult


object Chat: BotModule("聊天","与牛牛聊天") {

    // 学习关键词的数量
    private const val KEYWORD_SIZE = 2

    val messageCache = MessageCache()
    val contextCache = ContextCache()

    init {
        Bot.ONEBOT.groupRelation.groups.toList().forEach {
            Bot.LOGGER.info("Init group(${it.id}) last sync time.")
            messageCache.updateLastSyncTime(it.id.toLong())
        }

        event {
            on<OneBotNormalGroupMessageEvent> {
                val message = messageContent.plainText ?: ""
                if (message.length <= 2) {
                    Bot.LOGGER.info("Message($message) too short or not text, ignore.")
                    return@on EventResult.invalid()
                }

                if (IgnoreCommand.equals(message)) {
                    Bot.LOGGER.info("Message($message) is command, ignore.")
                    return@on EventResult.invalid()
                }

                val chatData = ChatData(
                    groupId.value,
                    userId.value,
                    rawMessage,
                    true,
                    message,
                    time.milliseconds,
                    bot.userId.toLong()
                )

                learn(chatData)
                EventResult.invalid()
            }
        }
    }

    private suspend fun learn(data: ChatData){
        // 获取群里的上一条发言
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
                        msg.userID == lastMessage.userID
                    }?.let { foundMsg ->
                        contextCache.upsert(
                            analyzeText(lastMessage.plainText!!),
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

    private fun reply(data: ChatData){
        if (data.plainText != "" && data.plainText!!.length < 2) {
            return
        }
    }

    @Task(15L)
    private fun autoSync(){
        messageCache.messageSyncToDatabase()
    }

}