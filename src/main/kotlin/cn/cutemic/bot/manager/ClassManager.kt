package cn.cutemic.bot.manager

import cn.cutemic.bot.Bot
import cn.cutemic.bot.module.BotModule
import org.reflections.Reflections


object ClassManager {
    val botModuleClasses: MutableSet<Class<out BotModule>> = Reflections("cn.cutemic.bot.module").getSubTypesOf(BotModule::class.java)

    init {
        Bot.LOGGER.info("Module classes: ${botModuleClasses.size}")
    }
}