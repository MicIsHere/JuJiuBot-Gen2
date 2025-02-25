package cn.cutemic.bot.model

object IgnoreCommand {

    val list = listOf(
        "牛牛管理",
        "牛牛人品",
        "牛牛塔罗牌",
        "牛牛喝酒",
        "牛牛干杯",
        "牛牛继续喝"
    )

    fun equals(message: String): Boolean{
        val command = message.split(" ").getOrNull(0) ?: return true
        list.forEach {
            if (command == it) {
                return true
            }
        }
        return false
    }

}