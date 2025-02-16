package cn.cutemic.bot.model.context


data class ContextEntry(
    val keywords: String,
    var count: Int,
    var lastUpdated: Long
) {
    constructor(keywords: String) : this(
        keywords = keywords,
        count = 0,
        lastUpdated = System.currentTimeMillis()
    )
}