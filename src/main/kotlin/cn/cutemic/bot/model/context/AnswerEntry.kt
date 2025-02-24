package cn.cutemic.bot.model.context

data class AnswerEntry(
    val id: String?,
    val group: String,
    var count: Int,
    val context: String,
    val message: String,
    var lastUsed: Long = System.currentTimeMillis(),
)
