package cn.cutemic.bot.model.context


data class ContextEntry(
    val keywords: String,
    val keywordsWeight: String,
    var count: Int,
    var lastUpdated: Long
) {
    constructor(keywords: String, keywordsWeight: String) : this(
        keywords = keywords,
        keywordsWeight = keywordsWeight,
        count = 0,
        lastUpdated = System.currentTimeMillis()
    )
}