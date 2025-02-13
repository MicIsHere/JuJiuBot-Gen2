package cn.cutemic.bot.event

import love.forte.simbot.application.Application
import love.forte.simbot.application.listeners
import love.forte.simbot.event.Event
import love.forte.simbot.event.EventResult
import love.forte.simbot.event.listen

class EventDSL {
    val listeners = mutableListOf<Application.() -> Unit>()

    inline fun <reified T : Event> on(
        crossinline handler: suspend T.() -> EventResult
    ) {
        listeners.add {
            listeners {
                listen<T> { event ->
                    event.handler()
                }
            }
        }
    }

    fun register(): Application.() -> Unit = {
        listeners.forEach { it() }
    }
}