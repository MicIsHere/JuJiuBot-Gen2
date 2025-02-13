package cn.cutemic.bot.manager

import cn.cutemic.bot.Bot
import cn.cutemic.bot.util.Task
import cn.cutemic.bot.util.TaskScope
import kotlinx.coroutines.*
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.isAccessible

object TaskManager {
    private val jobs = mutableListOf<Job>()

    fun tryRegister(obj: Any) {
        obj::class.members.forEach { member ->
            member.annotations.forEach { annotation ->
                if (annotation is Task) {
                    checkMember(member as KFunction<*>)
                    scheduleTask(obj, member, annotation.intervalSeconds)
                }
            }
        }
    }

    private fun checkMember(member: KFunction<*>) {
        require(member.parameters.size == 1) {
            "Method ${member.name} must have no parameters"
        }
    }

    private fun scheduleTask(obj: Any, method: KFunction<*>, interval: Long) {
        Bot.LOGGER.info("Register task ${obj.javaClass.name}")
        method.isAccessible = true

        val job = TaskScope.launch {
            while (isActive) {
                delay(interval * 1000)
                method.call(obj, null)
            }
        }
        jobs.add(job)
    }

    fun shutdown() {
        jobs.forEach { it.cancel() }
        TaskScope.cancel()
    }
}