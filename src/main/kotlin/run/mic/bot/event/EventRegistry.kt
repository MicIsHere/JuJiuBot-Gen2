package run.mic.bot.event

import love.forte.simbot.application.Application
import run.mic.bot.Trace

object EventRegistry {
    val handlers = mutableListOf<Application.() -> Unit>()

    fun Application.initAllEvents() {
        Trace.info("Init all module(s) event...")
        handlers.forEach { it() }
    }
}