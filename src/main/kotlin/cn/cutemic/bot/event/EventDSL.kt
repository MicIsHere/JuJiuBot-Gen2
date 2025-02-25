package cn.cutemic.bot.event

import cn.cutemic.bot.Bot
import cn.cutemic.bot.database.GroupService
import love.forte.simbot.application.Application
import love.forte.simbot.application.listeners
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.event.Event
import love.forte.simbot.event.EventResult
import love.forte.simbot.event.listen
import org.koin.java.KoinJavaComponent.inject

class EventDSL {
    val listeners = mutableListOf<Application.() -> Unit>()

    val groupService by inject<GroupService>(GroupService::class.java)

    inline fun <reified T : Event> on(
        crossinline handler: suspend T.() -> EventResult
    ) {
        listeners.add {
            listeners {
                listen<T> { event ->
                    when (event) {
                        is OneBotNormalGroupMessageEvent -> {
                            val messageEvent = event as OneBotNormalGroupMessageEvent
                            if (groupService.read(messageEvent.groupId.toLong())!!.soberUpTime != null) { // 醉酒检查，阻断事件传递
                                return@listen EventResult.empty()
                            }

                            if (groupService.read(messageEvent.groupId.toLong())!!.blocked != null) { // 群封禁检查，阻断事件传递
                                return@listen EventResult.empty()
                            }
                        }
                    }

                    event.handler()
                }
            }
        }
    }

    fun register(): Application.() -> Unit = {
        listeners.forEach { it() }
    }
}