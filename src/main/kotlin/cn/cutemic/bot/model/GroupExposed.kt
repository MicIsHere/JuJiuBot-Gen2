package cn.cutemic.bot.model

data class GroupExposed(
    val id: String?,
    val group: Long,
    val activity: Double,
    val drunk: Double,
    val soberUpTime: Long?,
    val blocked: Long?
)
