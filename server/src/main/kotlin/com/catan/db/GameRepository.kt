package com.catan.db

import com.catan.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class GameRecord(
    val id: String,
    val status: String,
    val hostPlayerId: String,
    val maxPlayers: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val winner: String? = null
)

data class GamePlayerRecord(
    val gameId: String,
    val playerId: String,
    val color: String,
    val seatIndex: Int
)

class GameRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    // In-memory cache of active game states
    val activeGames = ConcurrentHashMap<String, GameState>()

    fun createGame(hostPlayerId: String, maxPlayers: Int = 4): GameRecord {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        transaction {
            GamesTable.insert {
                it[GamesTable.id] = id
                it[GamesTable.status] = "LOBBY"
                it[GamesTable.hostPlayerId] = hostPlayerId
                it[GamesTable.maxPlayers] = maxPlayers
                it[GamesTable.gameState] = null
                it[GamesTable.winner] = null
                it[GamesTable.createdAt] = now
                it[GamesTable.updatedAt] = now
            }
        }

        return GameRecord(id, "LOBBY", hostPlayerId, maxPlayers, now, now)
    }

    fun getGame(gameId: String): GameRecord? = transaction {
        GamesTable.selectAll().where { GamesTable.id eq gameId }
            .firstOrNull()
            ?.let {
                GameRecord(
                    id = it[GamesTable.id],
                    status = it[GamesTable.status],
                    hostPlayerId = it[GamesTable.hostPlayerId],
                    maxPlayers = it[GamesTable.maxPlayers],
                    createdAt = it[GamesTable.createdAt],
                    updatedAt = it[GamesTable.updatedAt],
                    winner = it[GamesTable.winner]
                )
            }
    }

    fun listGames(status: String? = null): List<GameRecord> = transaction {
        val query = if (status != null) {
            GamesTable.selectAll().where { GamesTable.status eq status }
        } else {
            GamesTable.selectAll()
        }
        query.map {
            GameRecord(
                id = it[GamesTable.id],
                status = it[GamesTable.status],
                hostPlayerId = it[GamesTable.hostPlayerId],
                maxPlayers = it[GamesTable.maxPlayers],
                createdAt = it[GamesTable.createdAt],
                updatedAt = it[GamesTable.updatedAt],
                winner = it[GamesTable.winner]
            )
        }
    }

    fun updateGameStatus(gameId: String, status: String, winner: String? = null) {
        val now = System.currentTimeMillis()
        transaction {
            GamesTable.update({ GamesTable.id eq gameId }) {
                it[GamesTable.status] = status
                it[GamesTable.updatedAt] = now
                if (winner != null) {
                    it[GamesTable.winner] = winner
                }
            }
        }
    }

    fun addPlayerToGame(gameId: String, playerId: String, color: PlayerColor, seatIndex: Int) {
        transaction {
            GamePlayersTable.insert {
                it[GamePlayersTable.gameId] = gameId
                it[GamePlayersTable.playerId] = playerId
                it[GamePlayersTable.color] = color.name
                it[GamePlayersTable.seatIndex] = seatIndex
            }
        }
    }

    fun getGamePlayers(gameId: String): List<GamePlayerRecord> = transaction {
        GamePlayersTable.selectAll().where { GamePlayersTable.gameId eq gameId }
            .orderBy(GamePlayersTable.seatIndex)
            .map {
                GamePlayerRecord(
                    gameId = it[GamePlayersTable.gameId],
                    playerId = it[GamePlayersTable.playerId],
                    color = it[GamePlayersTable.color],
                    seatIndex = it[GamePlayersTable.seatIndex]
                )
            }
    }

    fun getGamePlayerCount(gameId: String): Int = transaction {
        GamePlayersTable.selectAll().where { GamePlayersTable.gameId eq gameId }.count().toInt()
    }

    fun isPlayerInGame(gameId: String, playerId: String): Boolean = transaction {
        GamePlayersTable.selectAll().where {
            (GamePlayersTable.gameId eq gameId) and (GamePlayersTable.playerId eq playerId)
        }.count() > 0
    }

    fun saveGameState(gameId: String, state: GameState) {
        activeGames[gameId] = state
        val stateJson = json.encodeToString(state)
        transaction {
            GamesTable.update({ GamesTable.id eq gameId }) {
                it[gameState] = stateJson
                it[updatedAt] = System.currentTimeMillis()
            }
        }
    }

    fun getGameState(gameId: String): GameState? {
        // Try in-memory cache first
        activeGames[gameId]?.let { return it }

        // Fall back to DB
        return transaction {
            GamesTable.selectAll().where { GamesTable.id eq gameId }
                .firstOrNull()
                ?.get(GamesTable.gameState)
                ?.let { jsonStr ->
                    json.decodeFromString<GameState>(jsonStr).also {
                        activeGames[gameId] = it
                    }
                }
        }
    }

    fun loadActiveGamesFromDb() {
        transaction {
            GamesTable.selectAll().where { GamesTable.status eq "ACTIVE" }
                .forEach { row ->
                    val gameId = row[GamesTable.id]
                    val stateJson = row[GamesTable.gameState]
                    if (stateJson != null) {
                        activeGames[gameId] = json.decodeFromString(stateJson)
                    }
                }
        }
    }

    fun logAction(gameId: String, playerId: String, actionType: String, actionData: String) {
        transaction {
            GameActionsTable.insert {
                it[GameActionsTable.gameId] = gameId
                it[GameActionsTable.playerId] = playerId
                it[GameActionsTable.actionType] = actionType
                it[GameActionsTable.actionData] = actionData
                it[GameActionsTable.timestamp] = System.currentTimeMillis()
            }
        }
    }
}
