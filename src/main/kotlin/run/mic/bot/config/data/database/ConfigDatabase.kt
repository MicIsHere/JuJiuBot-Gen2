package run.mic.bot.config.data.database

data class ConfigDatabase(
    val postgres: ConfigDatabasePostgres = ConfigDatabasePostgres(),
    val mongoDB: ConfigDatabaseMongoDB = ConfigDatabaseMongoDB()
)
