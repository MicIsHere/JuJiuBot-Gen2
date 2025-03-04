package cn.cutemic.bot.module.impl

import cn.cutemic.bot.module.BotModule
import cn.cutemic.bot.util.IgnoreCommand
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.event.EventResult
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

object LuckValue: BotModule("人品", "查看自己的人品值") {

    init {
        event {
            on<OneBotNormalGroupMessageEvent> {
                if (messageContent.plainText != "牛牛人品") {
                    return@on EventResult.empty()
                }
                if (IgnoreCommand.equals(messageContent.plainText)) {
                    return@on EventResult.empty()
                }
                val value = getValue(userId.toLong())
                reply("博士，今天的人品值是${getMessage(value)}")
                EventResult.empty()
            }
        }
    }

    private fun getValue(code: Long): Int {
        val today = LocalDate.now()
        val hash1 = "asdfgbn${today.dayOfYear}12#3\$45${today.year}IUY".hashCode().toDouble() / 3.0
        val hash2 = "QWERTY${code}0*8&6${today.dayOfMonth}kjhg".hashCode().toDouble() / 3.0
        val sum = (hash1 + hash2) / 527.0
        val num = abs(sum).roundToInt() % 1001
        return if (num >= 970) 100 else (num.toDouble() / 969 * 99).roundToInt()
    }

    private fun getMessage(code: Int): String {
        return when(code) {
            100 -> "$code！$code！$code！！！！！"
            99 -> "$code！但不是 100……"
            in 90..100 -> "$code！好评如潮！"
            in 80..100 -> "$code！不错啦不错啦！"
            in 60..100 -> "$code！是不错的一天呢！"
            50 -> "$code！五五开..."
            in 51..100 -> "$code！还行啦还行啦。"
            in 40..100 -> "$code！貌似还行？"
            in 20..100 -> "$code！还……还行吧……？"
            in 11..100 -> "$code！呜哇……"
            in 1..100 -> "$code……（没错，是百分制）"
            else -> "$code..."
        }
    }
}