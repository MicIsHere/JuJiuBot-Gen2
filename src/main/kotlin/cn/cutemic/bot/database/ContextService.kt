package cn.cutemic.bot.database

import cn.cutemic.bot.model.context.ContextEntry
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class ContextService(database: Database) {
    object Context: Table("context"){
        val id = varchar("id", 100)
        val keywords = text("keywords")
        val keywordsWeight = text("keywords_weight")
        val count = integer("count")
        val lastUpdated = long("last_updated")

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(Context)
            SchemaUtils.createMissingTablesAndColumns(Context)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun getId(keywords: String): String?{
        return dbQuery {
            Context.selectAll()
                .where(Context.keywords eq keywords)
                .map { it[Context.id] }
                .singleOrNull()
        }
    }

    suspend fun get(id: String): ContextEntry?{
        return dbQuery {
            Context.selectAll()
                .where(Context.id eq id)
                .map { ContextEntry(
                    it[Context.keywords],
                    it[Context.keywordsWeight],
                    it[Context.count],
                    it[Context.lastUpdated]
                ) }
                .singleOrNull()
        }
    }

    suspend fun add(entry: ContextEntry): String = dbQuery {
        Context.insert {
            it[id] = UUID.randomUUID().toString()
            it[keywords] = entry.keywords
            it[keywordsWeight] = entry.keywordsWeight
            it[count] = entry.count
            it[lastUpdated] = entry.lastUpdated
        }[Context.id]
    }

    suspend fun update(id: String, entry: ContextEntry) = dbQuery {
        Context.update({ Context.id eq id }) {
            it[keywords] = entry.keywords
            it[keywordsWeight] = entry.keywordsWeight
            it[count] = entry.count
            it[lastUpdated] = entry.lastUpdated
        }
    }

}