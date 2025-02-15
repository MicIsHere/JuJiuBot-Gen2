package cn.cutemic.bot.database

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
        val keywords = text("keywords")
        val group = varchar("group",36)
        val count = integer("count")
        val lastUsed = long("last_used")
        val context = reference("context_id", Context.id)  // 外键关联，简化操作

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

    suspend fun readIDByKeywords(keywords: String): String?{
        return dbQuery {
            Answer.selectAll()
                .where(Answer.keywords eq keywords)
                .map { it[Answer.id] }
                .singleOrNull()
        }
    }

    suspend fun add(entry: AnswerEntry, contextID: String): String = dbQuery {
        Answer.insert {
            it[id] = UUID.randomUUID().toString()
            it[keywords] = entry.keywords
            it[group] = entry.groupId
            it[count] = entry.count
            it[lastUsed] = entry.lastUsed
            it[context] = contextID
        }[Answer.id]
    }
}