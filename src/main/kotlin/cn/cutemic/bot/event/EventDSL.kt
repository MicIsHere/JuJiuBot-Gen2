package cn.cutemic.bot.event

import cn.cutemic.bot.Bot
import cn.cutemic.bot.database.GroupService
import kotlinx.coroutines.runBlocking
import love.forte.simbot.application.Application
import love.forte.simbot.application.listeners
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotFriendMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.event.Event
import love.forte.simbot.event.EventResult
import love.forte.simbot.event.listen
import org.koin.java.KoinJavaComponent.inject
import java.util.UUID

class EventDSL {
    val listeners = mutableListOf<Application.() -> Unit>()

    val groupService by inject<GroupService>(GroupService::class.java)

    inline fun <reified T : Event> on(
        crossinline handler: suspend T.() -> EventResult,
    ) {
        listeners.add {
            listeners {
                listen<T> { event ->
                    runCatching {
                        if (processEvent(event) != EventResult.invalid()) {
                            event.handler()
                        }
                    }.onFailure { error ->
                        processError(event, error)
                    }

                    EventResult.empty()
                }
            }
        }
    }

    fun register(): Application.() -> Unit = {
        listeners.forEach { it() }
    }

    fun processError(event: Event, throwable: Throwable): EventResult{
        val stackTrace = StringBuilder()
        val uuid = UUID.randomUUID()

        when (event) {
            is OneBotNormalGroupMessageEvent -> {
                throwable.stackTrace.forEach {
                    stackTrace.append("在 ${it.className}.${it.methodName}:${it.lineNumber}\n")
                }
                runBlocking {
                    event.reply("本次操作出现错误 (${throwable.message})：$throwable\n${stackTrace}\nError-ID: $uuid\n错误已被记录并通知了开发者。")
                }
            }

            is OneBotFriendMessageEvent -> {
                throwable.stackTrace.forEach {
                    stackTrace.append("在 ${it.className}.${it.methodName}:${it.lineNumber}\n")
                }
                runBlocking {
                    event.reply("本次操作出现错误 (${throwable.message})：$throwable\n${stackTrace}\nError-ID: $uuid\n错误已被记录并通知了开发者。")
                }
            }
        }

        Bot.LOGGER.fatal("Error-ID: $uuid")
        throwable.printStackTrace()
        stackTrace.clear()
        return EventResult.invalid()
    }

    fun processEvent(event: Event): EventResult{
        when (event) {
            is OneBotNormalGroupMessageEvent -> {
                runBlocking {
                    val group = groupService.read(event.groupId.toLong())!!
                    group.soberUpTime?.let { // 醉酒检查，阻断事件传递
                        return@runBlocking EventResult.invalid()
                    }

                    group.blocked?.let { // 群封禁检查，阻断事件传递
                        return@runBlocking EventResult.invalid()
                    }
                }
            }
        }
        return EventResult.empty()
    }
}