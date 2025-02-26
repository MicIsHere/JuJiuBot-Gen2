package cn.cutemic.bot.model.legacy.context

import org.bson.codecs.pojo.annotations.BsonProperty

@Deprecated("该数据类仅可用于迁移旧版数据库")
data class LegacyAnswers(
    @BsonProperty("keywords")
    val keywords: String,
    @BsonProperty("group_id")
    val groupID: Long,
    @BsonProperty("count")
    val count: Int,
    @BsonProperty("time")
    val time: Long,
    @BsonProperty("messages")
    val messages: List<String>,
)