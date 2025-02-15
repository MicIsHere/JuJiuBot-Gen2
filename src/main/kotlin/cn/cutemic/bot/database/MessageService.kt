package cn.cutemic.bot.database

import cn.cutemic.bot.model.MessageExposed
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class MessageService(database: Database) {
    object Message: Table("message"){
        val id = varchar("id",36)
        val bot = varchar("bot",36)
        val group = varchar("group",36)
        val user = long("user_id")
        val keywords = text("keywords")
        val plainText = text("plain_text").nullable()
        val rawMessage = text("raw_message")
        val time = long("time")

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(Message)
            SchemaUtils.createMissingTablesAndColumns(Message)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun addMany(message: List<MessageExposed>) = dbQuery {
        transaction {
            Message.batchInsert(
                message
            ) { (groupID, userID, rawMessage, keywords, plainText, time, botID) ->
                this[Message.id] = UUID.randomUUID().toString()
                this[Message.bot] = botID
                this[Message.group] = groupID
                this[Message.user] = userID
                this[Message.keywords] = keywords
                this[Message.plainText] = plainText
                this[Message.rawMessage] = rawMessage
                this[Message.time] = time
            }
        }
    }
}