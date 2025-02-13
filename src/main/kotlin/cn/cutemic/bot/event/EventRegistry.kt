package cn.cutemic.bot.event

import love.forte.simbot.application.Application

object EventRegistry {
    val handlers = mutableListOf<Application.() -> Unit>()

    fun Application.initAllEvents() {
        handlers.forEach { it() }
    }
}