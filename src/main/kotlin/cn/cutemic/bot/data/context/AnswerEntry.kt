package cn.cutemic.bot.data.context

import org.bson.codecs.pojo.annotations.BsonProperty

data class AnswerEntry(
    @BsonProperty("keywords")
    val keywords: String,
    @BsonProperty("group_id")
    val groupId: Long,
    @BsonProperty("count")
    var count: Int = 1,
    @BsonProperty("messages")
    val messages: MutableList<String>,
    @BsonProperty("last_used")
    var lastUsed: Long = System.currentTimeMillis()
)
