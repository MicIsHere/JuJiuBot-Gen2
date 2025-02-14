package cn.cutemic.bot.module.impl.chat

import cn.cutemic.bot.Bot
import cn.cutemic.bot.data.context.AnswerEntry
import cn.cutemic.bot.data.context.ContextEntry
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

class ContextCache {
    private val cache = ConcurrentHashMap<String, ContextEntry>()
    private val lock = ReentrantReadWriteLock()

    // 最小的上下文权重，小于这个值会被清理
    private val MIN_CONTEXT_COUNT = 5
    // 最小的回答权重，小于这个值会被清理
    private val MIN_ANSWER_COUNT = 2

    fun upsert(
        messageKeywords: String, // 发送消息的关键字
        replyKeywords: String, // 回答的关键字
        groupId: Long,
        message: String
    ) {
        lock.writeLock().withLock {
            val contextEntry = cache.getOrPut(messageKeywords) { // 寻找 context 的关键字，如果没有就加入下面那个新的
                ContextEntry(
                    keywords = messageKeywords,
                    answers = mutableListOf(),
                    count = 0,
                    lastUpdated = System.currentTimeMillis()
                ).let {
                    Bot.LOGGER.info("Add new context to cache: $it")
                    it
                }
            }

            // 找一样回答的关键字 并且是一个群的
            val answer = contextEntry.answers.find {
                it.keywords == replyKeywords && it.groupId == groupId
            }

            if (answer != null) {
                val oldAnswer = answer.count
                Bot.LOGGER.info("Update answer to cache: $answer")
                answer.count++
                Bot.LOGGER.info("Update count: $oldAnswer -> ${answer.count}")
                answer.messages.add(message)
                answer.lastUsed = System.currentTimeMillis()
            } else {
                contextEntry.answers.add( //找不到就自己开一个吧
                    AnswerEntry(
                        keywords = replyKeywords,
                        groupId = groupId,
                        count = 1,
                        messages = mutableListOf(message),
                        lastUsed = System.currentTimeMillis()
                    )
                )
            }


            contextEntry.count++
            contextEntry.lastUpdated = System.currentTimeMillis()
        }
    }

    fun find(keywords: String, minCount: Int): List<AnswerEntry> {
        lock.readLock().withLock {
            return cache[keywords]?.answers
                ?.filter { it.count >= minCount }
                ?.sortedByDescending { it.count }
                ?: emptyList()
        }
    }

    fun cleanup(expirationDays: Int = 15) {
        val cutoff = System.currentTimeMillis() - expirationDays * 24 * 3600 * 1000L

        lock.writeLock().withLock {
            cache.entries.removeAll { entry ->
                entry.value.lastUpdated < cutoff &&
                        entry.value.count < MIN_CONTEXT_COUNT
            }

            cache.values.forEach { context ->
                context.answers.removeAll { answer ->
                    answer.lastUsed < cutoff &&
                            answer.count < MIN_ANSWER_COUNT
                }
            }
        }
    }

    fun sync(){
        lock.readLock().withLock {
            if (cache.isEmpty()) {
                return
            }
            runBlocking {
                // 数据置入
            }
            cache.clear()
        }
    }
}