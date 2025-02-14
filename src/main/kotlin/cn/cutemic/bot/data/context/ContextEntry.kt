package cn.cutemic.bot.data.context

import org.bson.codecs.pojo.annotations.BsonProperty

data class ContextEntry(
    @BsonProperty("keywords")
    val keywords: String,
    @BsonProperty("answers")
    val answers: MutableList<AnswerEntry>,
    @BsonProperty("count")
    var count: Int,
    @BsonProperty("last_updated")
    var lastUpdated: Long
) {
    constructor(keywords: String) : this(
        keywords = keywords,
        answers = mutableListOf(),
        count = 0,
        lastUpdated = System.currentTimeMillis()
    )
}