package run.mic.bot.module.impl

import run.mic.bot.database.GroupService
import run.mic.bot.module.BotModule
import run.mic.bot.util.IgnoreCommand
import run.mic.bot.util.Task
import kotlinx.coroutines.runBlocking
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.event.EventResult
import org.koin.java.KoinJavaComponent.inject

object ShutUp : BotModule("闭嘴", "使牛牛临时安静五分钟") {

    private val groupService by inject<GroupService>(GroupService::class.java)

    init {
        event {
            on<OneBotNormalGroupMessageEvent> {
                if (IgnoreCommand.equals(rawMessage)) {
                    return@on EventResult.empty()
                }
                if (messageContent.plainText != "牛牛闭嘴") {
                    return@on EventResult.empty()
                }
                val groupID = groupService.read(groupId.toLong())?.id ?: return@on EventResult.empty()

                groupService.updateBlockedTime(groupID, 60 * 5)
                reply("博士...好，我会保持安静五分钟的。")
                EventResult.empty()
            }
        }
    }

    @Task(15)
    private fun onShutUp() {
        runBlocking {
            groupService
                .readAll()
                .forEach {
                    val result = if ((((it.blocked ?: 1) - 15) < 0)) null else it.blocked!! - 15
                    groupService.updateBlockedTime(it.id!!, result)
                }
        }
    }

}