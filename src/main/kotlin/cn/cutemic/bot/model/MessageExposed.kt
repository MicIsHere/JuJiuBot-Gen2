package cn.cutemic.bot.model
data class MessageExposed(
    val id: String?,
    val groupID: String,
    val userID: Long,
    val rawMessage: String,
    val keywords: String,
    val plainText: String?,
    val time: Long,
    val botID: String
)
