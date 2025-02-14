package cn.cutemic.bot.data

import org.bson.codecs.pojo.annotations.BsonProperty

data class ChatData(
    @BsonProperty("group_id")
    val groupID: Long,
    @BsonProperty("user_id")
    val userID: Long,
    @BsonProperty("raw_message")
    val rawMessage: String,
    @BsonProperty("is_plain_text")
    val isPlainText: Boolean,
    @BsonProperty("plain_text")
    val plainText: String?,
    @BsonProperty("time")
    val time: Long,
    @BsonProperty("bot_id")
    val botID: Long
)
