package run.mic.bot.manager

import org.reflections.Reflections
import run.mic.bot.module.BotModule
import run.mic.bot.util.Task

object ClassManager {
    val botModuleClasses = Reflections("run.mic.bot").getSubTypesOf(BotModule::class.java)
    val taskField = Reflections("run.mic.bot").getFieldsAnnotatedWith(Task::class.java)
}