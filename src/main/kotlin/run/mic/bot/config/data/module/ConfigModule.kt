package run.mic.bot.config.data.module

data class ConfigModule(
    val onErrorSendMsg: Boolean = true,
    val onErrorCrash: Boolean = false,
    val chat: ConfigChat = ConfigChat(),
    val deepChat: ConfigDeepChat = ConfigDeepChat(),
)