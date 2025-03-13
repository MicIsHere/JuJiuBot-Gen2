package run.mic.bot.manager

import run.mic.bot.Trace
import run.mic.bot.module.BotModule
import run.mic.bot.util.instance
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.system.measureTimeMillis

object ModuleManager {
    private val botModuleInstance = CopyOnWriteArrayList<BotModule>()

    fun perloadModule() {
        Trace.info("Preload module")
        val time = measureTimeMillis {
            ClassManager.botModuleClasses
                .forEach { module ->
                    runCatching {
                        TaskManager.tryRegister(module.instance!!)
                        registerModule(module.instance!!)
                    }.onFailure {
                        Trace.error("Module ${module.name} encountered a error while registering, and System has stopped module load!")
                        throw it
                    }
                }
        }
        Trace.info("${botModuleInstance.size} module(s) found, used ${time}ms")
    }

    fun registerModule(botModule: BotModule) {
        botModuleInstance.add(botModule)
    }

    fun unregisterModule(botModule: BotModule) {
        botModuleInstance.remove(botModule)
    }

}