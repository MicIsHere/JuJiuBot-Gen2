package run.mic.bot.model

data class BlockExposed(
    val id: String?,
    val bot: String,
    val group: String?,
    val userID: Long?,
    val answer: String,
    val reason: String,
    val time: Long
)
