package run.mic.bot.model.context


data class ContextEntry(
    val id: String?,
    val keywords: String,
    val keywordsWeight: String,
    var count: Int,
    var lastUpdated: Long,
    val legacyID: String?
) {
    constructor(id: String?, keywords: String, keywordsWeight: String) : this(
        id = id,
        keywords = keywords,
        keywordsWeight = keywordsWeight,
        count = 0,
        lastUpdated = System.currentTimeMillis(),
        legacyID = null
    )
}