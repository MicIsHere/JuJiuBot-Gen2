package cn.cutemic.bot.module.impl

import cn.cutemic.bot.module.BotModule
import love.forte.simbot.event.ChatGroupMessageEvent
import love.forte.simbot.event.EventResult

object SayHello: BotModule("牛牛你好", "跟牛牛打招呼") {

    init {
        event {
            on<ChatGroupMessageEvent> {
                if (this.messageContent.plainText == "牛牛你好") {
                    reply("你好呀")
                }

                EventResult.empty()
            }
        }

    }

}