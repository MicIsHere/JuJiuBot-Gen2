package cn.cutemic.bot.model

import com.google.gson.annotations.SerializedName

data class Response(
    @SerializedName("id") val id: String,
    @SerializedName("object") val obj: String,
    @SerializedName("created") val created: Long,
    @SerializedName("model") val model: String,
    @SerializedName("choices") val choices: List<Choice>,
    @SerializedName("usage") val usage: Usage,
    @SerializedName("system_fingerprint") val systemFingerprint: String
)

data class Choice(
    @SerializedName("index") val index: Int,
    @SerializedName("message") val message: Message,
    @SerializedName("logprobs") val logProbs: Any?,
    @SerializedName("finish_reason") val finishReason: String
)

data class Message(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int,
    @SerializedName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails,
    @SerializedName("prompt_cache_hit_tokens") val promptCacheHitTokens: Int,
    @SerializedName("prompt_cache_miss_tokens") val promptCacheMissTokens: Int
)

data class PromptTokensDetails(
    @SerializedName("cached_tokens") val cachedTokens: Int
)
