package cn.cutemic.bot.database

import cn.cutemic.bot.model.GroupExposed
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class GroupService(database: Database) {
    object Group : Table("group") {
        val id = varchar("id", 36)
        val group = long("group")
        val activity = double("activity").default(0.0)
        val drunk = double("drunk").default(0.0)
        val soberUpTime = long("soberup_time").nullable()
        val blocked = long("blocked").nullable()

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

    suspend fun add(groupExposed: GroupExposed): String = dbQuery {
        Group.insert {
            it[group] = groupExposed.group
            it[id] = UUID.randomUUID().toString()
            it[activity] = groupExposed.activity
        }[Group.id]
    }

    suspend fun read(id: Long): GroupExposed? {
        return dbQuery {
            Group.selectAll()
                .where(Group.group eq id)
                .map {
                    GroupExposed(
                        it[Group.id],
                        it[Group.group],
                        it[Group.activity],
                        it[Group.drunk],
                        it[Group.soberUpTime],
                        it[Group.blocked]
                    )
                }
                .singleOrNull()
        }
    }

    suspend fun read(id: String): GroupExposed? {
        return dbQuery {
            Group.selectAll()
                .where(Group.id eq id)
                .map {
                    GroupExposed(
                        it[Group.id],
                        it[Group.group],
                        it[Group.activity],
                        it[Group.drunk],
                        it[Group.soberUpTime],
                        it[Group.blocked]
                    )
                }
                .singleOrNull()
        }
    }

    suspend fun readAll(): List<GroupExposed> {
        return dbQuery {
            Group.selectAll()
                .map {
                    GroupExposed(
                        it[Group.id],
                        it[Group.group],
                        it[Group.activity],
                        it[Group.drunk],
                        it[Group.soberUpTime],
                        it[Group.blocked]
                    )
                }
        }
    }

    suspend fun updateActivity(id: String, activity: Double) = dbQuery {
        Group.update({ Group.id eq id }) {
            it[Group.activity] = activity
        }
    }

    suspend fun updateDrunk(id: String, drunk: Double) = dbQuery {
        Group.update({ Group.id eq id }) {
            it[Group.drunk] = drunk
        }
    }

    suspend fun updateSoberUpTime(id: String, time: Long?) = dbQuery {
        Group.update({ Group.id eq id }) {
            it[soberUpTime] = time
        }
    }

    suspend fun updateBlockedTime(id: String, time: Long?) = dbQuery {
        Group.update({ Group.id eq id }) {
            it[blocked] = time
        }
    }
}