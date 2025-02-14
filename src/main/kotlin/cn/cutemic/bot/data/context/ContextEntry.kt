package cn.cutemic.bot.data.context

import kotlinx.serialization.SerialName

data class ContextEntry(
    @SerialName("keywords")
    val keywords: String,
    @SerialName("answers")
    val answers: MutableList<AnswerEntry>,
    @SerialName("count")
    var count: Int,
    @SerialName("last_updated")
    var lastUpdated: Long
) {
    constructor(keywords: String) : this(
        keywords = keywords,
        answers = mutableListOf(),
        count = 0,
        lastUpdated = System.currentTimeMillis()
    )
}