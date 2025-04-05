package run.mic.bot.config.data.module

data class ConfigDeepChat(
    // 是否启用深度聊天
    val enable: Boolean = true,
    // 使用的服务。目前支持: DeepSeek
    val useService: String = "deepseek",
    // 服务密钥
    val token: String = "",
)
