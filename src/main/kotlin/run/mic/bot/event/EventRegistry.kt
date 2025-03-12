package run.mic.bot.event

import love.forte.simbot.application.Application
import run.mic.bot.Bot.Companion.LOGGER

object EventRegistry {
    val handlers = mutableListOf<Application.() -> Unit>()

    fun Application.initAllEvents() {
        LOGGER.info("Init all module(s) event...")
        handlers.forEach { it() }
    }
}