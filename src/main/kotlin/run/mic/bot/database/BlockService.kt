package run.mic.bot.database

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import run.mic.bot.Bot
import run.mic.bot.Trace
import run.mic.bot.model.BlockExposed
import java.util.*

class BlockService(database: Database) {
    object Block : Table("block") {
        val id = varchar("id", 36)
        val bot = varchar("bot", 36)
        val group = varchar("group", 36).nullable()
        val userID = long("user_id").nullable()
        val answer = text("answer")
        val reason = text("reason")
        val time = long("time")

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(Block)
            SchemaUtils.createMissingTablesAndColumns(Block)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun add(block: BlockExposed) = dbQuery {
        Block.insert {
            it[id] = UUID.randomUUID().toString()
            it[bot] = block.bot
            it[group] = block.group
            it[userID] = block.userID
            it[answer] = block.answer
            it[reason] = block.reason
            it[time] = System.currentTimeMillis()
        }[Block.id].let {
            if (Bot.DATABASE_DEBUG) Trace.info("Blocked answer id: ${block.answer}")
            it
        }
    }
}