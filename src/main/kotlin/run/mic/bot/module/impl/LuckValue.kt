package run.mic.bot.module.impl

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotNormalGroupMessageEvent
import love.forte.simbot.event.EventResult
import run.mic.bot.module.BotModule
import run.mic.bot.util.IgnoreCommand
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
                reply(getMessage(value))
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
            100 -> "哎呀，博士，您的运气是$code！非同凡响的好运，尽情的享受鲜花，美酒与欢呼吧！"
            in 80..99 -> "$code，极好的运气呢。我仿佛感受到了高歌与欢呼。享受美好的一天吧。"
            in 60..80 -> "$code 运气不错。您的路上会有智慧与勇气相伴，放心的前进吧。"
            in 40..60 -> "您的运气是$code，胜利还需由智慧和努力浇灌，不过也能期待一下偶尔的小幸运呢。"
            in 20..40 -> "博士，您今天的运气是$code。不必追求荣耀与胜利，普普通通的也不错，不是吗？"
            in 1..20 -> "您的运气是……$code，不算太好，需要我为您祈求好运吗？"
            else -> "唔，运气是$code，不是很好呢。不必担心，我已经为您祈求好运了。可以陪我一起逛逛庆典吗？"
        }
    }
}