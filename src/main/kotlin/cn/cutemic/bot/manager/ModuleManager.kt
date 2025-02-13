package cn.cutemic.bot.manager

import cn.cutemic.bot.Bot
import cn.cutemic.bot.module.BotModule
import cn.cutemic.bot.util.Task
import cn.cutemic.bot.util.instance
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.system.measureTimeMillis

object ModuleManager {
    val botModuleInstance = CopyOnWriteArrayList<BotModule>()

    fun perLoadModule(){
        Bot.LOGGER.info("Preload module")
        val time = measureTimeMillis {
            ClassManager.botModuleClasses
                .forEach { module ->
                    runCatching {
                        registerModule(module.instance!!)
                        TaskManager.tryRegister(module.instance!!)
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