package cn.cutemic.bot.model.context

data class AnswerEntry(
    val keywords: String,
    val groupId: String,
    var count: Int,
    val messages: MutableList<String>,
    var lastUsed: Long = System.currentTimeMillis(),
)
