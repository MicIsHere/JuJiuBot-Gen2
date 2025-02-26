package cn.cutemic.bot.model.legacy.context

import org.bson.codecs.pojo.annotations.BsonProperty

@Deprecated("该数据类仅可用于迁移旧版数据库")
data class LegacyBan(
    @BsonProperty("keywords")
    val keywords: String,
    @BsonProperty("group_id")
    val groupID: Long,
    @BsonProperty("reason")
    val reason: String,
    @BsonProperty("time")
    val time: Long
)