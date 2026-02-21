package com.catan.ws

import com.catan.ai.AiController
import com.catan.db.GameRepository
import com.catan.db.PlayerRepository
import com.catan.game.GameEngine
import com.catan.model.*
import com.catan.routes.filterStateForPlayer
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.*

fun Routing.gameWebSocket(
    gameRepo: GameRepository,
    playerRepo: PlayerRepository,
    sessionManager: GameSessionManager,
    engine: GameEngine,
    aiController: AiController = AiController(engine)
) {
    webSocket("/games/{gameId}/ws") {
        val gameId = call.parameters["gameId"]
        val token = call.request.queryParameters["token"]

        if (gameId == null || token == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing gameId or token"))
            return@webSocket
        }

        val player = playerRepo.findByToken(token)
        if (player == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
            return@webSocket
        }

        // Verify player is in this game
        if (!gameRepo.isPlayerInGame(gameId, player.id)) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Not in this game"))
            return@webSocket
        }

        // Register session
        sessionManager.addSession(gameId, player.id, this)

        // Send current state if game is active
        val currentState = gameRepo.getGameState(gameId)
        if (currentState != null) {
            val filtered = filterStateForPlayer(currentState, player.id)
            sessionManager.sendToPlayer(gameId, player.id, ServerEvent.GameStateUpdate(filtered))
        }

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    handleAction(gameId, player.id, text, gameRepo, sessionManager, engine, aiController)
                }
            }
        } finally {
            sessionManager.removeSession(gameId, player.id)
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

private suspend fun handleAction(
    gameId: String,
    playerId: String,
    text: String,
    gameRepo: GameRepository,
    sessionManager: GameSessionManager,
    engine: GameEngine,
    aiController: AiController
) {
    try {
        // Parse the incoming JSON and inject the playerId (client doesn't send it)
        val jsonElement = json.parseToJsonElement(text)
        if (jsonElement !is JsonObject) {
            sessionManager.sendToPlayer(gameId, playerId, ServerEvent.Error("Invalid message format"))
            return
        }

        val enriched = JsonObject(jsonElement.jsonObject.toMutableMap().apply {
            put("playerId", JsonPrimitive(playerId))
        })

        val action: GameAction = json.decodeFromJsonElement(enriched)

        val currentState = gameRepo.getGameState(gameId)
        if (currentState == null) {
            sessionManager.sendToPlayer(gameId, playerId, ServerEvent.Error("Game not found"))
            return
        }

        val result = engine.execute(currentState, action)

        if (result.isFailure) {
            val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
            sessionManager.sendToPlayer(gameId, playerId, ServerEvent.Error(errorMsg))
            return
        }

        val newState = result.getOrThrow()

        // Persist to DB + cache
        gameRepo.saveGameState(gameId, newState)

        // Log action
        val actionType = jsonElement.jsonObject["type"]?.jsonPrimitive?.content ?: "UNKNOWN"
        gameRepo.logAction(gameId, playerId, actionType, text)

        // If game finished, update game status
        if (newState.phase == GamePhase.FINISHED) {
            gameRepo.updateGameStatus(gameId, "FINISHED")
        }

        // Broadcast filtered state to each player
        for (p in newState.players) {
            val filtered = filterStateForPlayer(newState, p.id)
            sessionManager.sendToPlayer(gameId, p.id, ServerEvent.GameStateUpdate(filtered))
        }

        // Send supplementary delta events
        sendDeltaEvents(gameId, playerId, action, currentState, newState, sessionManager)

        // Trigger AI turns if the next player is AI
        aiController.onStateChanged(
            gameId = gameId,
            state = newState,
            saveState = { gId, s ->
                gameRepo.saveGameState(gId, s)
                if (s.phase == GamePhase.FINISHED) {
                    gameRepo.updateGameStatus(gId, "FINISHED")
                }
            },
            broadcastState = { s ->
                for (p in s.players) {
                    val filtered = filterStateForPlayer(s, p.id)
                    sessionManager.sendToPlayer(gameId, p.id, ServerEvent.GameStateUpdate(filtered))
                }
            }
        )

    } catch (e: Exception) {
        sessionManager.sendToPlayer(gameId, playerId, ServerEvent.Error(e.message ?: "Server error"))
    }
}

private suspend fun sendDeltaEvents(
    gameId: String,
    playerId: String,
    action: GameAction,
    oldState: GameState,
    newState: GameState,
    sessionManager: GameSessionManager
) {
    when (action) {
        is GameAction.RollDice -> {
            val roll = newState.diceRoll
            if (roll != null) {
                sessionManager.broadcast(gameId, ServerEvent.DiceRolled(roll.first, roll.second, playerId))
            }
        }
        is GameAction.PlaceSettlement, is GameAction.PlaceCity -> {
            val newBuildings = newState.buildings - oldState.buildings.toSet()
            for (b in newBuildings) {
                sessionManager.broadcast(gameId, ServerEvent.BuildingPlaced(b))
            }
        }
        is GameAction.PlaceRoad -> {
            val newRoads = newState.roads - oldState.roads.toSet()
            for (r in newRoads) {
                sessionManager.broadcast(gameId, ServerEvent.RoadPlaced(r))
            }
        }
        is GameAction.OfferTrade -> {
            val trade = newState.pendingTrade
            if (trade != null) {
                sessionManager.broadcast(gameId, ServerEvent.TradeOffered(trade))
            }
        }
        is GameAction.EndTurn -> {
            val currentPlayer = newState.players[newState.currentPlayerIndex]
            sessionManager.broadcast(gameId, ServerEvent.TurnChanged(currentPlayer.id, newState.currentPlayerIndex))
        }
        else -> { /* No special delta event */ }
    }

    // Check for game over
    if (newState.phase == GamePhase.FINISHED && oldState.phase != GamePhase.FINISHED) {
        val winner = newState.players[newState.currentPlayerIndex]
        sessionManager.broadcast(gameId, ServerEvent.GameOver(winner.id, winner.displayName))
    }
}
