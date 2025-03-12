package run.mic.bot.manager

import run.mic.bot.module.BotModule
import run.mic.bot.util.Task
import org.reflections.Reflections

object ClassManager {
    val botModuleClasses = Reflections("run.mic.bot").getSubTypesOf(BotModule::class.java)
    val taskField = Reflections("run.mic.bot").getFieldsAnnotatedWith(Task::class.java)
}