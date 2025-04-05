package run.mic.bot.config.data.database

data class ConfigDatabasePostgres(
    val host: String = "localhost",
    val port: Int = 5432,
    val databaseName: String = "jujiubot",
    val user: String = "jujiubot",
    val password: String = "jujiubot"
)
