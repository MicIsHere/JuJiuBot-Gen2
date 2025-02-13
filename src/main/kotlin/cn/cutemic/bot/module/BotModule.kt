package cn.cutemic.bot.module

import cn.cutemic.bot.event.EventDSL
import cn.cutemic.bot.event.EventRegistry

abstract class BotModule(
    name: CharSequence,
    description: CharSequence,
) {
    fun event(block: EventDSL.() -> Unit) {
        val dsl = EventDSL().apply(block)
        EventRegistry.handlers.add(dsl.register())
    }
}
