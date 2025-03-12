package run.mic.bot.manager

import run.mic.bot.Bot
import run.mic.bot.module.BotModule
import run.mic.bot.util.instance
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.system.measureTimeMillis

object ModuleManager {
    private val botModuleInstance = CopyOnWriteArrayList<BotModule>()

    fun perloadModule() {
        Bot.LOGGER.info("Preload module")
        val time = measureTimeMillis {
            ClassManager.botModuleClasses
                .forEach { module ->
                    runCatching {
                        TaskManager.tryRegister(module.instance!!)
                        registerModule(module.instance!!)
                    }.onFailure {
                        Bot.LOGGER.error("Module ${module.name} encountered a error while registering, and System has stopped module load!")
                        throw it
                    }
                }
        }
        Bot.LOGGER.info("${botModuleInstance.size} module(s) found, used ${time}ms")
    }

    fun registerModule(botModule: BotModule) {
        botModuleInstance.add(botModule)
    }

    fun unregisterModule(botModule: BotModule) {
        botModuleInstance.remove(botModule)
    }

}