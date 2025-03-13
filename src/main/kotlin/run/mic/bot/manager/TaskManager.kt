package run.mic.bot.manager

import kotlinx.coroutines.*
import run.mic.bot.Trace
import run.mic.bot.util.Task
import run.mic.bot.util.scope.TaskScope
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.isAccessible

object TaskManager {
    private val jobs = mutableListOf<Job>()

    fun loadTask() {
        Trace.info("Loading task...")
        ClassManager.taskField.forEach {
            tryRegister(it)
        }
    }

    fun tryRegister(obj: Any) {
        obj::class.members.forEach { member ->
            member.annotations.forEach { annotation ->
                if (annotation is Task) {
                    Trace.info("Register task ${obj.javaClass.name}.${member.name}")
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
        method.isAccessible = true

        val job = TaskScope.launch {
            while (isActive) {
                delay(interval * 1000)
                runCatching {
                    method.call(obj)
                }.onFailure {
                    if (it is IllegalStateException) {
                        Trace.warn("Cannot invoke task ${obj.javaClass.`package`}, trying use 'null' args invoke.")
                        method.call(obj, null)
                        return@launch
                    }
                    throw it
                }
            }
        }
        jobs.add(job)
    }

    fun shutdown() {
        jobs.forEach { it.cancel() }
        TaskScope.cancel()
    }
}