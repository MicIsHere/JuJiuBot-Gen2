package cn.cutemic.bot.data

import kotlinx.serialization.SerialName

data class ChatData(
    @SerialName("group_id")
    val groupID: Long,
    @SerialName("user_id")
    val userID: Long,
    @SerialName("raw_message")
    val rawMessage: String,
    @SerialName("plain_text")
    val plainText: String,
    @SerialName("time")
    val time: Long,
    @SerialName("bot_id")
    val botID: Long
)
