package run.mic.bot

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.Configurator

private val lazyLogger by lazy {
    LogManager.getLogger("JuJiuBot").apply {
        Configurator.setLevel(name, Bot.LOG_LEVEL)
    }
}

object Trace : Logger by lazyLogger