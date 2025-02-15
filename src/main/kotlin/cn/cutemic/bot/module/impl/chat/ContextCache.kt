package cn.cutemic.bot.module.impl.chat

import cn.cutemic.bot.Bot
import cn.cutemic.bot.database.AnswerService
import cn.cutemic.bot.database.ContextService
import cn.cutemic.bot.model.context.AnswerEntry
import cn.cutemic.bot.model.context.ContextEntry
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

class ContextCache {
    private val cache = ConcurrentHashMap<String, ContextEntry>()
    private val lock = ReentrantReadWriteLock()

    private val contextService by inject<ContextService>(ContextService::class.java)
    private val answerService by inject<AnswerService>(AnswerService::class.java)

    // 最小的上下文权重，小于这个值会被清理
    private val MIN_CONTEXT_COUNT = 5
    // 最小的回答权重，小于这个值会被清理
    private val MIN_ANSWER_COUNT = 2

    fun upsert(
        messageKeywords: String, // 发送消息的关键字
        replyKeywords: String, // 回答的关键字
        groupId: String,
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
            }
            contextEntry.answers.add( //找不到就自己开一个吧
                AnswerEntry(
                    keywords = replyKeywords,
                    groupId = groupId,
                    count = 1,
                    messages = mutableListOf(message),
                    lastUsed = System.currentTimeMillis()
                ).let {
                    Bot.LOGGER.info("Added new answer: $it")
                    it
                }
            )

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
            cache.values.forEach { context1 ->
                runBlocking {
                    val context = contextService.readIDByKeywords(context1.keywords) ?: contextService.add(context1)

                    context1.answers.forEach {
                        val answer = answerService.readIDByKeywords(it.keywords)

                        if (answer == null) {
                            answerService.add(it, context)
                        }
                    }
                }
            }
            cache.clear()
        }
    }
}