package run.mic.bot

import org.jetbrains.exposed.sql.Database
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import run.mic.bot.config.data.database.ConfigDatabase
import run.mic.bot.database.*

class KoinBootstrap(val config: ConfigDatabase) {

    fun start() {
        startKoin {
            modules(database())
        }
        Trace.info("Koin 加载中...")
    }

    private fun database(): Module {
        return module {
            single {
                Trace.info("连接数据库...")
                Database.connect(
                    "jdbc:pgsql://${config.postgres.host}:${config.postgres.port}/${config.postgres.databaseName}",
                    driver = "com.impossibl.postgres.jdbc.PGDriver",
                    user = config.postgres.user,
                    password = config.postgres.password
                )
            }

            single {
                GroupService(get())
            }

            single {
                BotService(get())
            }

            single {
                MessageService(get())
            }

            single {
                AnswerService(get())
            }

            single {
                ContextService(get())
            }

            single {
                BlockService(get())
            }
        }
    }

}