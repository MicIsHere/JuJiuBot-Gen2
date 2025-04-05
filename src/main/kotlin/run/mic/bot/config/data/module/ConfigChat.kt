package run.mic.bot.config.data.module

data class ConfigChat(
    // 是否启用聊天模块
    val enable: Boolean = true,
    // 学习关键词数量
    val keywordSize: Int = 2,
    // N 个群有相同的回复，就跨群作为全局回复
    val crossGroupThreshold: Int = 2,
    // 基础回复概率
    val baseReplyProb: Double = 0.4,
    // 话题重要性
    val topicsImportance: Double = 0.5,
    // 回复分句概率
    val splitProbability: Double = 0.3,
    // 跳过学习概率
    val ignoreLearn: Double = 0.05,
    // 调用HanLP API需要的密钥。在 jieba 分词失效的情况下使用HanLP分词，否则使用原文本，不使用请保持空
    val hanLPToken: String = "",
)
