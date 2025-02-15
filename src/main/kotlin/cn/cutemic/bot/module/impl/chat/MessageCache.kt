package cn.cutemic.bot.module.impl.chat

import cn.cutemic.bot.Bot
import cn.cutemic.bot.database.MessageService
import cn.cutemic.bot.model.MessageExposed
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

class MessageCache {
    private val service by inject<MessageService>(MessageService::class.java)

    private val cache = ConcurrentHashMap<String, MutableList<MessageExposed>>()
    private val lastSyncTime = ConcurrentHashMap<String,Long>()
    private val lock = ReentrantReadWriteLock()
    // 最大信息缓存数量
    private val MAX_GROUP_MESSAGE_CACHE_SIZE = 5
    // 最大同步时间，超过该时间将强制同步
    private val MAX_GROUP_MESSAGE_CACHE_SYNC_TIME = 60L

    fun updateLastSyncTime(groupId: String){
        lastSyncTime[groupId] = System.currentTimeMillis().let {
            Bot.LOGGER.info("Update group($groupId) last sync-time is $it")
            it
        }
    }

    fun getLastSyncTime(): Map<String,Long>{
        return lastSyncTime
    }

    fun addMessage(data: MessageExposed) {
        Bot.LOGGER.info("Add message to cache: $data")
        lock.writeLock().withLock {
            val messages = cache.getOrPut(data.groupID) { mutableListOf() }
            messages.add(data)
            if (messages.size > 5) {
                Bot.LOGGER.info("Message cache is full, remove older cache.")
                messages.removeAt(0)
            }
        }
    }

    fun get(groupId: String): List<MessageExposed> {
        lock.readLock().withLock {
            return cache[groupId]?.toList() ?: emptyList()
        }
    }

    fun messageSyncToDatabase(){
        cache.forEach { (groupID, groupCache) ->
            if (groupCache.isEmpty()){
                return
            }

            if (groupCache.size >= MAX_GROUP_MESSAGE_CACHE_SIZE) {
                Bot.LOGGER.info("Group $groupID message cache is full, syncing to database...")
                runBlocking {
                    service.addMany(groupCache)
                    updateLastSyncTime(groupID)
                }
                groupCache.clear()
            }

            if (System.currentTimeMillis() - (lastSyncTime[groupID] ?: System.currentTimeMillis()) >= MAX_GROUP_MESSAGE_CACHE_SYNC_TIME) {
                Bot.LOGGER.info("Group $groupID sync-time is out limit, syncing to database...")
                runBlocking {
                    service.addMany(groupCache)
                    updateLastSyncTime(groupID)
                }
                groupCache.clear()
            }
        }
    }
}