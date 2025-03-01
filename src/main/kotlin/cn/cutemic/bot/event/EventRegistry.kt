package cn.cutemic.bot.event

import cn.cutemic.bot.Bot.Companion.LOGGER
import love.forte.simbot.application.Application

object EventRegistry {
    val handlers = mutableListOf<Application.() -> Unit>()

    fun Application.initAllEvents() {
        LOGGER.info("Init all module(s) event...")
        handlers.forEach { it() }
    }
}