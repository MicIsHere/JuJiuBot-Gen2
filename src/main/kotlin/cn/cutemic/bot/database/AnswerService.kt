package cn.cutemic.bot.database

import cn.cutemic.bot.Bot
import cn.cutemic.bot.database.ContextService.Context
import cn.cutemic.bot.model.context.AnswerEntry
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class AnswerService(database: Database) {
    object Answer: Table("answer"){
        val id = varchar("id",36)
        val group = varchar("group",36)
        val count = integer("count")
        val lastUsed = long("last_used")
        val context = reference("context", Context.id)
        val message = reference("message", MessageService.Message.id)

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(Answer)
            SchemaUtils.createMissingTablesAndColumns(Answer)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    // 使用上下文ID获取所有回答
    suspend fun getAnswerByContextId(id: String): List<AnswerEntry>{
        return dbQuery {
            Answer.selectAll()
                .where(Answer.context eq id)
                .map { AnswerEntry(it[Answer.id], it[Answer.group], it[Answer.count], it[Answer.context], it[Answer.message], it[Answer.lastUsed]) }
        }
    }

    suspend fun get(id: String): AnswerEntry?{
        return dbQuery {
            Answer.selectAll()
                .where(Answer.id eq id)
                .map { AnswerEntry(it[Answer.id], it[Answer.group], it[Answer.count], it[Answer.context], it[Answer.message], it[Answer.lastUsed]) }
                .singleOrNull()
        }
    }

    // 添加一个回答
    suspend fun add(entry: AnswerEntry): String = dbQuery {
        Answer.insert {
            it[id] = UUID.randomUUID().toString()
            it[group] = entry.group
            it[count] = entry.count
            it[lastUsed] = entry.lastUsed
            it[context] = entry.context
            it[message] = entry.message
        }[Answer.id].let {
            Bot.LOGGER.info("Answer added to database $entry")
            it
        }
    }

    suspend fun updateCount(answerID: String, count: Int) = dbQuery {
        Answer.update({ Answer.id eq answerID }) {
            it[id] = id
            it[group] = group
            it[Answer.count] = count
            it[lastUsed] = lastUsed
            it[context] = context
            it[message] = message
        }
    }
}