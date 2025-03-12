package run.mic.bot.database

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class BotService(database: Database) {
    object Bot : Table("bot") {
        val id = varchar("id", 36)
        val bot = long("bot")

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(Bot)
            SchemaUtils.createMissingTablesAndColumns(Bot)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun add(id: Long): String = dbQuery {
        Bot.insert {
            it[bot] = id
            it[Bot.id] = UUID.randomUUID().toString()
        }[Bot.id]
    }

    suspend fun read(id: Long): String? {
        return dbQuery {
            Bot.selectAll()
                .where(Bot.bot eq id)
                .map { it[Bot.id] }
                .singleOrNull()
        }
    }

    suspend fun change(id: Long, newId: String) {
        return dbQuery {
            Bot.update({ Bot.bot eq id }) {
                it[Bot.id] = newId
            }
        }
    }
}