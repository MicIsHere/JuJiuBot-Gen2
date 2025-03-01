package cn.cutemic.bot.module.impl

import cn.cutemic.bot.database.GroupService
import cn.cutemic.bot.module.BotModule
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.event.EventResult
import love.forte.simbot.message.safePlainText
import org.koin.java.KoinJavaComponent.inject

object Admin: BotModule("管理","用于管理牛牛") {

    private val result = StringBuilder()

    private val service by inject<GroupService>(GroupService::class.java)

    init {
        event {
            on<OneBotNormalGroupMessageEvent> {
                if (!messageContent.safePlainText.startsWith("牛牛管理")) {
                    return@on EventResult.empty()
                }

                result.clear()
                result.append("操作成功完成。\n")

                val command = messageContent.safePlainText.split(" ").getOrNull(1) ?: return@on EventResult.invalid()
                when (command) {
                    "crashtest" -> {
                        null!!
                    }

                    else -> {
                        result.append("没有该指令。")
                    }
                }

                reply(result.toString())
                EventResult.empty()
            }
        }
    }

}