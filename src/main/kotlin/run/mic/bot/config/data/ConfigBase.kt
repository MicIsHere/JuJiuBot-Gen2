package run.mic.bot.config.data

import run.mic.bot.config.data.database.ConfigDatabase
import run.mic.bot.config.data.module.ConfigModule

data class ConfigBase(
    // 运行版本
    val version: String = "2.0.0",
    // 调试模式
    val debugMode: Boolean = false,
    val protocol: ConfigProtocol = ConfigProtocol(),
    val database: ConfigDatabase = ConfigDatabase(),
    val module: ConfigModule = ConfigModule()
)