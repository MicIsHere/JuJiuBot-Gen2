package run.mic.bot.module.impl

import kotlinx.coroutines.runBlocking
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.event.EventResult
import org.koin.java.KoinJavaComponent.inject
import run.mic.bot.Trace
import run.mic.bot.database.GroupService
import run.mic.bot.module.BotModule
import run.mic.bot.util.Task
import java.util.concurrent.ThreadLocalRandom

object Drunk : BotModule("喝酒", "灌醉牛牛") {

    /* 运行时变量 */
    private val random = ThreadLocalRandom.current() // 脱离线程随机
    private val service by inject<GroupService>(GroupService::class.java)

    init {
        event {
            on<OneBotNormalGroupMessageEvent> {
                if (messageContent.plainText != "牛牛喝酒" && messageContent.plainText != "牛牛干杯" && messageContent.plainText != "牛牛继续喝") {
                    return@on EventResult.empty()
                }

                val group = service.read(groupId.toLong())!!
                val drunk = group.drunk + random.nextDouble(0.0, 0.3)

                val goToSleep = random.nextDouble() < if (group.drunk <= 5.0) {
                    0.02
                } else {
                    (group.drunk - 5.0 + 0.1) * 0.2
                }
                if (goToSleep) {
                    // 3.5 是期望的最大醉酒程度
                    val sleepDuration = (minOf(group.drunk, 3.5) + random.nextDouble()) * 80
                    Trace.info("System go to sleep in group $groupId, wake up after $sleepDuration sec")
                    reply("呀，博士。你今天走起路来，怎么看着摇...摇...晃......")
                    reply("Zzz...")
                    service.updateSoberUpTime(group.id!!, sleepDuration.toLong())
                    return@on EventResult.empty()
                }

                service.updateDrunk(group.id!!, drunk)
                reply("呀，博士。你今天走起路来，怎么看着摇摇晃晃的？")
                EventResult.empty()
            }
        }
    }

    /*
     * 尝试醒酒
     *
     * 每分钟执行一次，对醉酒系数减少0.1至0.3
     */
    @Task(60)
    fun trySoberUp() {
        runBlocking {
            service.readAll()
                .forEach {
                    if (it.drunk == 0.0) {
                        return@runBlocking
                    }

                    val number = random.nextDouble(0.1, 0.3)
                    val result = if (((it.drunk - number) < 0)) 0.0 else it.drunk - number
                    service.updateDrunk(it.id!!, result)
                    val time = it.soberUpTime ?: return@runBlocking
                    if (time <= 60) {
                        service.updateSoberUpTime(it.id, null)
                        return@runBlocking
                    }
                    service.updateSoberUpTime(it.id, time - 60)
                }
        }
    }

}