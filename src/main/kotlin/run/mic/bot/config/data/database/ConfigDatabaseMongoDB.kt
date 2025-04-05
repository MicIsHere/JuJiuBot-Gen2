package run.mic.bot.config.data.database

data class ConfigDatabaseMongoDB(
    val host: String = "localhost",
    val port: Int = 5432,
    val databaseName: String = "jujiubot",
    // 迁移旧数据
    val transferOldData: Boolean = false,
)
