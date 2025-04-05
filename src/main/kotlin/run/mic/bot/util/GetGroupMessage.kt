package run.mic.bot.util

import com.google.gson.annotations.SerializedName

data class ApiResponse(
    val status: String,
    val retcode: Int,
    @SerializedName("data") val data: DataContent,
    val echo: Any?
)

data class DataContent(
    @SerializedName("messages") val messages: List<MessageItem>?
)

data class MessageItem(
    @SerializedName("raw_message") val rawMessage: String?
)