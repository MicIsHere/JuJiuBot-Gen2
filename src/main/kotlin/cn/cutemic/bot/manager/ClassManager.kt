package cn.cutemic.bot.manager

import cn.cutemic.bot.module.BotModule
import cn.cutemic.bot.util.Task
import org.reflections.Reflections

object ClassManager {
    val botModuleClasses = Reflections("cn.cutemic.bot").getSubTypesOf(BotModule::class.java)
    val taskField = Reflections("cn.cutemic.bot").getFieldsAnnotatedWith(Task::class.java)
}