package cn.cutemic.bot.data

object IgnoreCommand {

    val list = listOf(
        "牛牛管理"
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