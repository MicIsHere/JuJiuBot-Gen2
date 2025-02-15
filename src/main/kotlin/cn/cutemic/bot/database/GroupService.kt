package cn.cutemic.bot.database

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class GroupService(database: Database) {
    object Group: Table("group"){
        val id = varchar("id",36)
        val group = long("group")

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(Group)
            SchemaUtils.createMissingTablesAndColumns(Group)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun add(id: Long): String = dbQuery {
        Group.insert {
            it[group] = id
            it[Group.id] = UUID.randomUUID().toString()
        }[Group.id]
    }

    suspend fun read(id: Long): String?{
        return dbQuery {
            Group.selectAll()
                .where(Group.group eq id)
                .map { it[Group.id] }
                .singleOrNull()
        }
    }
}