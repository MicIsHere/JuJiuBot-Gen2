package run.mic.bot.module

import run.mic.bot.event.EventDSL
import run.mic.bot.event.EventRegistry

abstract class BotModule(
    name: CharSequence,
    description: CharSequence,
) {
    fun event(block: EventDSL.() -> Unit) {
        val dsl = EventDSL().apply(block)
        EventRegistry.handlers.add(dsl.register())
    }
}
