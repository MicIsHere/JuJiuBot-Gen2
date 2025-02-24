package cn.cutemic.bot.model.legacy

import org.bson.codecs.pojo.annotations.BsonProperty

data class LegacyMessageData(
    @BsonProperty("group_id")
    val groupID: String,
    @BsonProperty("user_id")
    val userID: String,
    @BsonProperty("raw_message")
    val rawMessage: String,
    @BsonProperty("keywords")
    val keywords: String,
    @BsonProperty("is_plain_text")
    val isPlainText: String,
    @BsonProperty("plain_text")
    val plainText: String?,
    @BsonProperty("time")
    val time: String,
    @BsonProperty("bot_id")
    val botID: String
)