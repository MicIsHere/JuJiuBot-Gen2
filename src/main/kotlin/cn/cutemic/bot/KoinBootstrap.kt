package cn.cutemic.bot

import cn.cutemic.bot.database.*
import org.jetbrains.exposed.sql.Database
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

class KoinBootstrap {

    fun start() {
        startKoin {
            modules(database())
        }
        Bot.LOGGER.info("Koin started!")
    }

    private fun database(): Module {
        return module {
            single {
                Bot.LOGGER.info("Connect database...")
//                Database.connect(
//                    "jdbc:pgsql://192.168.100.220:5432/jujiubot",
//                    driver = "com.impossibl.postgres.jdbc.PGDriver",
//                    user = "user_tPMaQ5",
//                    password = "password_j5RBbH"
//                )
                Database.connect(
                    "jdbc:pgsql://localhost:5432/jujiubot",
                    driver = "com.impossibl.postgres.jdbc.PGDriver",
                    user = "postgres",
                    password = "mic2333"
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