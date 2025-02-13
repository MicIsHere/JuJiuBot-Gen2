package cn.cutemic.bot.module.impl

import cn.cutemic.bot.Bot
import cn.cutemic.bot.data.ChatData
import cn.cutemic.bot.data.IgnoreCommand
import cn.cutemic.bot.module.BotModule
import cn.cutemic.bot.util.Task
import com.hankcs.hanlp.restful.HanLPClient
import com.qianxinyao.analysis.jieba.keyword.Keyword
import com.qianxinyao.analysis.jieba.keyword.TFIDFAnalyzer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import love.forte.simbot.common.id.toLong
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.event.EventResult
import java.util.concurrent.ConcurrentHashMap


object Chat: BotModule("聊天","与牛牛聊天") {

    private const val KEYWORD_SIZE = 2

    val messageCache = ConcurrentHashMap<Long, MutableList<ChatData>>()
    private val mutex = Mutex()

    init {
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
        // 群里的上一条发言
        val lastMessage = getGroupMessages(data.groupID).let {
            if (it.isEmpty()) {
                Bot.LOGGER.info("Context is empty, ignore!")
                addMessageToCache(data)
                return
            }
            it.last()
        }

        addMessageToCache(data)
        // 关键词分析 本地失败将切换至HanLP
        val keywordList = Bot.TFIDF.analyze(data.plainText, KEYWORD_SIZE)
        if (keywordList.isEmpty()) {
            Bot.LOGGER.warn("TD-IDF Analyze is failed, trying use HanLp API...")
            runCatching {
                Bot.HAN_LP.keyphraseExtraction(data.plainText).forEach {
                    keywordList.add(Keyword(
                        it.key,
                        it.value
                    ))
                }
            }.onFailure {
                // HanLP分析失败 使用原文本当作上下文
                Bot.LOGGER.error("HanLp Analyze is failed, use raw-text...")
                keywordList.add(Keyword(
                    data.plainText,
                    1.0
                ))
            }
        }

        // 上一条发言用户不是这次数据的用户 将尝试获取前三条数据
        if (lastMessage.userID != data.userID) {
            runCatching {
                getGroupMessages(data.groupID).asReversed()
                    .take(2)
                    .firstOrNull { msg ->
                        requireNotNull(msg.userID) { "Invalid user ID in message" }
                        msg.userID == lastMessage.userID
                    }?.let { foundMsg ->
                        //置入数据
                        Bot.LOGGER.info("Insect context: ${foundMsg.plainText}")
                    }
            }.onFailure {
                Bot.LOGGER.error("Error processing messages: ${it.message}")
            }
        }

        Bot.LOGGER.info(Bot.MONGO_DB.getCollection<ChatData>("message").find())

        val respone = StringBuilder()

        keywordList.forEach {
            respone.append("${it.name}(${it.tfidfvalue}) | ")
        }
        respone.append("Context: ${lastMessage.plainText} - ${data.plainText}")

        Bot.LOGGER.info("Learned message $respone")
    }


    private suspend fun reply(data: ChatData){
        if (data.plainText != "" && data.plainText!!.length < 2) {
            return
        }


    }

    @Task(60L)
    private suspend fun messageSyncToDatabase(){
        Bot.LOGGER.info("Syncing message cache to database...")
        Bot.MONGO_DB.getCollection<ChatData>("message")
    }

    private suspend fun addMessageToCache(data: ChatData) {
        Bot.LOGGER.info("Add message to cache: $data")
        mutex.withLock {
            messageCache.getOrPut(data.groupID) { mutableListOf() }.add(data)
        }
    }

    private fun getGroupMessages(groupId: Long): List<ChatData> {
        return messageCache[groupId]?.toList() ?: emptyList()
    }

}