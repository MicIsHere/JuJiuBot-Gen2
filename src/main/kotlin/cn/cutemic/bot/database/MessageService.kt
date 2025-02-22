package cn.cutemic.bot.database

import cn.cutemic.bot.Bot
import cn.cutemic.bot.model.MessageExposed
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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

    suspend fun add(message: MessageExposed) = dbQuery {
        Message.insert {
            it[id] = UUID.randomUUID().toString()
            it[bot] = message.botID
            it[group] = message.groupID
            it[user] = message.userID
            it[keywords] = message.keywords
            it[plainText] = message.plainText
            it[rawMessage] = message.rawMessage
            it[time] = message.time
        }[Message.id].let {
            Bot.LOGGER.info("Message added to database with ID: $it")
            it
        }
    }

    suspend fun read(id: String): MessageExposed?{
        return dbQuery {
            Message.selectAll()
                .where(Message.id eq id)
                .map { MessageExposed(
                    it[Message.id],
                    it[Message.group],
                    it[Message.user],
                    it[Message.rawMessage],
                    it[Message.keywords],
                    it[Message.plainText],
                    it[Message.time],
                    it[Message.bot]
                ) }
                .singleOrNull()
        }
    }

    suspend fun readLastMessages(id: String, messageCount: Int): List<MessageExposed>{
        return dbQuery {
            Message.selectAll()
                .where(Message.group eq id)
                .sortedByDescending { Message.time }
                .take(50)
                .map {
                    MessageExposed(
                        it[Message.id],
                        it[Message.group],
                        it[Message.user],
                        it[Message.rawMessage],
                        it[Message.keywords],
                        it[Message.plainText],
                        it[Message.time],
                        it[Message.bot]
                    )
                }
        }
    }

    suspend fun readListByGroupID(group: String): List<MessageExposed>{
        return dbQuery {
            Message.selectAll()
                .where(Message.group eq group)
                .map {
                    MessageExposed(
                        it[Message.id],
                        it[Message.group],
                        it[Message.user],
                        it[Message.rawMessage],
                        it[Message.keywords],
                        it[Message.plainText],
                        it[Message.time],
                        it[Message.bot]
                    )
                }
        }
    }
}