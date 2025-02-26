package cn.cutemic.bot.model.legacy.context

import org.bson.codecs.pojo.annotations.BsonProperty

@Deprecated("该数据类仅可用于迁移旧版数据库")
data class LegacyContext(
    @BsonProperty("keywords")
    val keywords: String,
    @BsonProperty("time")
    val time: Long,
    @BsonProperty("count")
    val count: Int,
    @BsonProperty("answers")
    val answers: List<LegacyAnswers>,
    @BsonProperty("clear_time")
    val clearTime: Long?,
    @BsonProperty("ban")
    val ban: List<LegacyBan>?,
)