package cn.cutemic.bot.util

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Task(val intervalSeconds: Long)
