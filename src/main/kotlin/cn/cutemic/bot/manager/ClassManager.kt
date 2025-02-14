package cn.cutemic.bot.manager

import cn.cutemic.bot.Bot
import cn.cutemic.bot.module.BotModule
import cn.cutemic.bot.util.Task
import org.reflections.Reflections


object ClassManager {
    val botModuleClasses = Reflections("cn.cutemic.bot").getSubTypesOf(BotModule::class.java)
    val taskField = Reflections("cn.cutemic.bot").getFieldsAnnotatedWith(Task::class.java)

    init {
        Bot.LOGGER.info("Module classes: ${botModuleClasses.size}")
        if (taskField.isEmpty()) {
            Bot.LOGGER.warn("Cannot get task field!")
        }
    }
}