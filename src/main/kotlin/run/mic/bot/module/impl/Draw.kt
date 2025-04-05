package run.mic.bot.module.impl

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.event.EventResult
import run.mic.bot.module.BotModule
import run.mic.bot.util.IgnoreCommand

object Draw: BotModule("牛牛画图","画图") {

    init {
        event {
            on<OneBotNormalGroupMessageEvent> {
                if (messageContent.plainText != "牛牛画画") {
                    return@on EventResult.empty()
                }

                if (IgnoreCommand.equals(rawMessage)){
                    return@on EventResult.empty()
                }
                reply("画图")
                EventResult.empty()
            }
        }
    }
}