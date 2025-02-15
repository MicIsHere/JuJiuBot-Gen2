package cn.cutemic.bot.model.context


data class ContextEntry(
    val keywords: String,
    val answers: MutableList<AnswerEntry>,
    var count: Int,
    var lastUpdated: Long
) {
    constructor(keywords: String) : this(
        keywords = keywords,
        answers = mutableListOf(),
        count = 0,
        lastUpdated = System.currentTimeMillis()
    )
}