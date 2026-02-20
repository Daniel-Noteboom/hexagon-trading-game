package com.catan.ws

import com.catan.model.ServerEvent
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class GameSessionManager {

    private val json = Json { ignoreUnknownKeys = true }

    // gameId → (playerId → WebSocket session)
    private val sessions = ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketServerSession>>()

    fun addSession(gameId: String, playerId: String, session: WebSocketServerSession) {
        sessions.getOrPut(gameId) { ConcurrentHashMap() }[playerId] = session
    }

    fun removeSession(gameId: String, playerId: String) {
        sessions[gameId]?.remove(playerId)
        if (sessions[gameId]?.isEmpty() == true) {
            sessions.remove(gameId)
        }
    }

    fun getSession(gameId: String, playerId: String): WebSocketServerSession? {
        return sessions[gameId]?.get(playerId)
    }

    fun getGameSessions(gameId: String): Map<String, WebSocketServerSession> {
        return sessions[gameId] ?: emptyMap()
    }

    fun getPlayerCount(gameId: String): Int {
        return sessions[gameId]?.size ?: 0
    }

    suspend fun broadcast(gameId: String, event: ServerEvent) {
        val message = json.encodeToString(event)
        sessions[gameId]?.values?.forEach { session ->
            try {
                session.send(Frame.Text(message))
            } catch (_: Exception) {
                // Session might be closed; ignore
            }
        }
    }

    suspend fun sendToPlayer(gameId: String, playerId: String, event: ServerEvent) {
        val message = json.encodeToString(event)
        sessions[gameId]?.get(playerId)?.let { session ->
            try {
                session.send(Frame.Text(message))
            } catch (_: Exception) {
                // Session might be closed
            }
        }
    }
}
