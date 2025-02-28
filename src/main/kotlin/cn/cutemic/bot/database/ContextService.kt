package cn.cutemic.bot.database

import cn.cutemic.bot.Bot
import cn.cutemic.bot.database.ContextService.Context.count
import cn.cutemic.bot.database.ContextService.Context.id
import cn.cutemic.bot.database.ContextService.Context.keywords
import cn.cutemic.bot.database.ContextService.Context.keywordsWeight
import cn.cutemic.bot.database.ContextService.Context.lastUpdated
import cn.cutemic.bot.database.ContextService.Context.legacyID
import cn.cutemic.bot.model.context.ContextEntry
import cn.cutemic.bot.model.fast.FastContextEntry
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

// 问题上下文
class ContextService(database: Database) {
    object Context: Table("context"){
        val id = varchar("id", 100)
        val keywords = text("keywords")
        val keywordsWeight = text("keywords_weight")
        val count = integer("count")
        val lastUpdated = long("last_updated")
        val legacyID = varchar("legacy_id",50).nullable() // 兼容旧版本

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
                .map { it[id] }
                .singleOrNull()
        }
    }

    suspend fun get(id: String): ContextEntry?{
        return dbQuery {
            Context.selectAll()
                .where(Context.id eq id)
                .map { ContextEntry(
                    it[Context.id],
                    it[Context.keywords],
                    it[Context.keywordsWeight],
                    it[Context.count],
                    it[Context.lastUpdated],
                    it[Context.legacyID]
                ) }
                .singleOrNull()
        }
    }

    suspend fun add(entry: ContextEntry): String = dbQuery {
        Context.insert {
            it[id] = entry.id ?: UUID.randomUUID().toString()
            it[keywords] = entry.keywords
            it[keywordsWeight] = entry.keywordsWeight
            it[count] = entry.count
            it[lastUpdated] = entry.lastUpdated
        }[id]
    }

    suspend fun update(entry: ContextEntry) = dbQuery {
        Context.update({ id eq entry.id!! }) {
            it[keywords] = entry.keywords
            it[keywordsWeight] = entry.keywordsWeight
            it[count] = entry.count
            it[lastUpdated] = entry.lastUpdated
        }
    }

    suspend fun fastReadAll(): List<FastContextEntry>{
        return dbQuery {
            Context.selectAll()
                .map {
                    FastContextEntry(
                        it[id],
                        it[keywords],
                        it[keywordsWeight],
                        it[count],
                        it[lastUpdated],
                        it[legacyID]
                    )
                }
        }
    }

    suspend fun addMany(message: List<ContextEntry>, ignoreError: Boolean, batchSize: Int = 1000) = dbQuery {
        for (i in message.indices step batchSize) {
            val batch = message.subList(i, minOf(i + batchSize, message.size))
            runCatching {
                Context.batchInsert(batch, ignoreError, shouldReturnGeneratedValues = true) { data ->
                    this@batchInsert[id] = data.id ?: UUID.randomUUID().toString()
                    this@batchInsert[Context.keywords] = cleanNullBytes(data.keywords)!!
                    this@batchInsert[Context.keywordsWeight] = cleanNullBytes(data.keywordsWeight)!!
                    this@batchInsert[Context.count] = data.count
                    this@batchInsert[Context.lastUpdated] = data.lastUpdated
                    this@batchInsert[Context.legacyID] = data.legacyID
                }
            }.onFailure {
                Bot.LOGGER.error("On batch $i failed.")
                println(batch)
                throw it
            }
        }
    }

    private fun cleanNullBytes(input: String?): String? {
        return input?.replace("\u0000", "") // 去掉所有的空字节字符
    }
}