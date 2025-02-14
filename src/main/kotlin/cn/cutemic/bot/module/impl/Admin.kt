package cn.cutemic.bot.module.impl

import cn.cutemic.bot.Bot
import cn.cutemic.bot.module.BotModule
import cn.cutemic.bot.module.impl.chat.Chat
import love.forte.simbot.common.id.toLong
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.event.EventResult
import love.forte.simbot.message.MessagesBuilder
import love.forte.simbot.message.safePlainText

object Admin: BotModule("管理","用于管理牛牛") {

    init {
        event {
            on<OneBotNormalGroupMessageEvent> {
                if (!messageContent.safePlainText.startsWith("牛牛管理")) {
                    return@on EventResult.empty()
                }

                val command = messageContent.safePlainText.split(" ").getOrNull(1) ?: return@on EventResult.invalid()
                when (command) {
                    "sendmsgcache" -> {
                        if (Chat.messageCache.get(groupId.toLong()).isEmpty()) {
                            Bot.LOGGER.info("MessageCache is empty.")
                            reply("该群的MessageCache为空。")
                            return@on EventResult.empty()
                        }

                        val result = MessagesBuilder
                            .create()
                            .add("Result:\n")
                        Chat.messageCache.get(groupId.toLong()).forEach {
                            result.add("$it\n")
                        }
                        result.add("---结束---")
                        reply(result.build())
                    }

                    "sendsynctime" -> {
                        val result = MessagesBuilder
                            .create()
                            .add("Last database sync-time:\n")

                        Chat.messageCache.getLastSyncTime().forEach { (t, u) ->
                            result.add("$t -> $u\n")
                        }

                        result.add("---结束---")
                        reply(result.build())
                    }

                    else -> {
                        reply("没有该指令。")
                    }
                }

                EventResult.empty()
            }
        }
    }

}