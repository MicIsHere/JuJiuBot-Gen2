package run.mic.bot.database

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import run.mic.bot.Bot
import run.mic.bot.model.MessageExposed
import run.mic.bot.model.fast.FastMessageExposed
import java.util.*

class MessageService(database: Database) {
    object Message : Table("message") {
        val id = varchar("id", 36)
        val bot = varchar("bot", 36)
        val group = varchar("group", 36)
        val user = long("user_id").nullable() // 兼容旧版数据库
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
            it[id] = message.id ?: UUID.randomUUID().toString()
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

    suspend fun addMany(message: List<MessageExposed>, ignoreError: Boolean, batchSize: Int = 1000) = dbQuery {
        for (i in message.indices step batchSize) {
            val batch = message.subList(i, minOf(i + batchSize, message.size))
            runCatching {
                Message.batchInsert(batch, ignoreError, shouldReturnGeneratedValues = true) { data ->
                    this@batchInsert[Message.id] = data.id ?: UUID.randomUUID().toString()
                    this@batchInsert[Message.bot] = cleanNullBytes(data.botID)!!
                    this@batchInsert[Message.group] = cleanNullBytes(data.groupID)!!
                    this@batchInsert[Message.user] = data.userID
                    this@batchInsert[Message.keywords] = cleanNullBytes(data.keywords)!!
                    this@batchInsert[Message.plainText] = cleanNullBytes(data.plainText)
                    this@batchInsert[Message.rawMessage] = cleanNullBytes(data.rawMessage) ?: ""
                    this@batchInsert[Message.time] = data.time
                }.let { message ->
                    Bot.LOGGER.info("Success ${message.size}/$batchSize")
                }
            }.onFailure {
                Bot.LOGGER.error("On batch $i failed.")
                println(batch)
                throw it
            }
        }
    }


    suspend fun read(id: String): MessageExposed? {
        return dbQuery {
            Message.selectAll()
                .where(Message.id eq id)
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
                .singleOrNull()
        }
    }

    suspend fun readLastMessages(id: String, messageCount: Int): List<MessageExposed> {
        return dbQuery {
            Message.selectAll()
                .where(Message.group eq id)
                .sortedByDescending { Message.time }
                .take(messageCount)
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

    suspend fun readListByGroupID(group: String): List<MessageExposed> {
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

    private fun cleanNullBytes(input: String?): String? {
        return input?.replace("\u0000", "") // 去掉所有的空字节字符
    }

    suspend fun readAll(): List<MessageExposed> {
        return dbQuery {
            Message.selectAll()
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

    suspend fun fastReadAll(): List<FastMessageExposed> {
        return dbQuery {
            Message.selectAll()
                .map {
                    FastMessageExposed(
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

    @Deprecated("该函数仅可用于迁移旧版数据库")
    suspend fun searchMessageOnLegacyDatabaseTransform(groupID: String, message: String, time: Long): MessageExposed? {
        return dbQuery {
            Message.selectAll()
                .where((Message.group eq groupID) and (Message.rawMessage eq message) and (Message.time eq time))
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
                .singleOrNull()
        }
    }

    suspend fun changeBot(id: String, newId: String) {
        return dbQuery {
            Message.update({ Message.bot eq id }) {
                it[bot] = newId
            }
        }
    }
}