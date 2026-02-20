package com.catan.db

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

data class PlayerRecord(
    val id: String,
    val displayName: String,
    val sessionToken: String,
    val createdAt: Long
)

class PlayerRepository {

    fun createPlayer(displayName: String): PlayerRecord {
        val id = UUID.randomUUID().toString()
        val token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        val now = System.currentTimeMillis()

        transaction {
            PlayersTable.insert {
                it[PlayersTable.id] = id
                it[PlayersTable.displayName] = displayName
                it[PlayersTable.sessionToken] = token
                it[PlayersTable.createdAt] = now
            }
        }

        return PlayerRecord(id, displayName, token, now)
    }

    fun findByToken(token: String): PlayerRecord? = transaction {
        PlayersTable.selectAll().where { PlayersTable.sessionToken eq token }
            .firstOrNull()
            ?.let {
                PlayerRecord(
                    id = it[PlayersTable.id],
                    displayName = it[PlayersTable.displayName],
                    sessionToken = it[PlayersTable.sessionToken],
                    createdAt = it[PlayersTable.createdAt]
                )
            }
    }

    fun findById(id: String): PlayerRecord? = transaction {
        PlayersTable.selectAll().where { PlayersTable.id eq id }
            .firstOrNull()
            ?.let {
                PlayerRecord(
                    id = it[PlayersTable.id],
                    displayName = it[PlayersTable.displayName],
                    sessionToken = it[PlayersTable.sessionToken],
                    createdAt = it[PlayersTable.createdAt]
                )
            }
    }
}
