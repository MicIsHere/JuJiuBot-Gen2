package cn.cutemic.bot.model

import kotlinx.serialization.SerialName

data class MessageExposed(
    @SerialName("group_id")
    val groupID: String,
    @SerialName("user_id")
    val userID: Long,
    @SerialName("raw_message")
    val rawMessage: String,
    @SerialName("keywords")
    val keywords: String,
    @SerialName("plain_text")
    val plainText: String?,
    @SerialName("time")
    val time: Long,
    @SerialName("bot_id")
    val botID: String
)
