package cn.cutemic.bot.data.context

import kotlinx.serialization.SerialName

data class AnswerEntry(
    @SerialName("keywords")
    val keywords: String,
    @SerialName("group_id")
    val groupId: Long,
    @SerialName("count")
    var count: Int,
    @SerialName("messages")
    val messages: MutableList<String>,
    @SerialName("last_used")
    var lastUsed: Long = System.currentTimeMillis()
)
