package run.mic.bot.model.context

data class AnswerEntry(
    val id: String?,
    val group: String?, // 空的群ID代表是全局回复
    var count: Int,
    val context: String,
    val message: String,
    var lastUsed: Long = System.currentTimeMillis(),
)
