package run.mic.bot.config.data

data class ConfigProtocol(
    val botUniqueId: Long = 10001,
    val apiServerHost: String = "http://127.0.0.1:7999",
    val eventServerHost: String = "ws://127.0.0.1:8080/onebot/v11/ws/"
)