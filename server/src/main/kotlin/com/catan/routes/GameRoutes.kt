package com.catan.routes

import com.catan.db.GameRepository
import com.catan.db.PlayerRepository
import com.catan.game.BoardGenerator
import com.catan.model.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CreateGameRequest(val maxPlayers: Int = 4)

@Serializable
data class CreateGameResponse(val gameId: String)

@Serializable
data class GameInfoResponse(
    val gameId: String,
    val status: String,
    val hostPlayerId: String,
    val maxPlayers: Int,
    val players: List<GamePlayerInfo>
)

@Serializable
data class GamePlayerInfo(val playerId: String, val displayName: String, val color: String, val seatIndex: Int)

@Serializable
data class JoinGameResponse(val color: String, val seatIndex: Int)

@Serializable
data class GameListResponse(val games: List<GameInfoResponse>)

fun Routing.gameRoutes(gameRepo: GameRepository, playerRepo: PlayerRepository) {

    fun authenticatePlayer(token: String?): com.catan.db.PlayerRecord? {
        if (token.isNullOrBlank()) return null
        return playerRepo.findByToken(token)
    }

    post("/games") {
        val token = call.request.header("X-Session-Token")
        val player = authenticatePlayer(token)
        if (player == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid session token"))
            return@post
        }

        val request = try {
            call.receive<CreateGameRequest>()
        } catch (_: Exception) {
            CreateGameRequest()
        }

        if (request.maxPlayers !in 2..4) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "maxPlayers must be 2-4"))
            return@post
        }

        val game = gameRepo.createGame(player.id, request.maxPlayers)
        // Auto-add the host as the first player
        gameRepo.addPlayerToGame(game.id, player.id, PlayerColor.RED, 0)

        call.respond(HttpStatusCode.OK, CreateGameResponse(game.id))
    }

    get("/games") {
        val status = call.request.queryParameters["status"]
        val games = gameRepo.listGames(status)
        val gameInfos = games.map { game ->
            val gamePlayers = gameRepo.getGamePlayers(game.id)
            val playerInfos = gamePlayers.map { gp ->
                val p = playerRepo.findById(gp.playerId)
                GamePlayerInfo(gp.playerId, p?.displayName ?: "Unknown", gp.color, gp.seatIndex)
            }
            GameInfoResponse(game.id, game.status, game.hostPlayerId, game.maxPlayers, playerInfos)
        }
        call.respond(HttpStatusCode.OK, GameListResponse(gameInfos))
    }

    get("/games/{gameId}") {
        val gameId = call.parameters["gameId"]!!
        val game = gameRepo.getGame(gameId)
        if (game == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Game not found"))
            return@get
        }

        val gamePlayers = gameRepo.getGamePlayers(gameId)
        val playerInfos = gamePlayers.map { gp ->
            val p = playerRepo.findById(gp.playerId)
            GamePlayerInfo(gp.playerId, p?.displayName ?: "Unknown", gp.color, gp.seatIndex)
        }

        call.respond(HttpStatusCode.OK, GameInfoResponse(game.id, game.status, game.hostPlayerId, game.maxPlayers, playerInfos))
    }

    post("/games/{gameId}/join") {
        val token = call.request.header("X-Session-Token")
        val player = authenticatePlayer(token)
        if (player == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid session token"))
            return@post
        }

        val gameId = call.parameters["gameId"]!!
        val game = gameRepo.getGame(gameId)
        if (game == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Game not found"))
            return@post
        }

        if (game.status != "LOBBY") {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Game is not in lobby"))
            return@post
        }

        if (gameRepo.isPlayerInGame(gameId, player.id)) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Already in this game"))
            return@post
        }

        val currentCount = gameRepo.getGamePlayerCount(gameId)
        if (currentCount >= game.maxPlayers) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Game is full"))
            return@post
        }

        val colors = PlayerColor.entries.toList()
        val usedColors = gameRepo.getGamePlayers(gameId).map { it.color }
        val availableColor = colors.first { it.name !in usedColors }
        val seatIndex = currentCount

        gameRepo.addPlayerToGame(gameId, player.id, availableColor, seatIndex)
        call.respond(HttpStatusCode.OK, JoinGameResponse(availableColor.name, seatIndex))
    }

    post("/games/{gameId}/start") {
        val token = call.request.header("X-Session-Token")
        val player = authenticatePlayer(token)
        if (player == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid session token"))
            return@post
        }

        val gameId = call.parameters["gameId"]!!
        val game = gameRepo.getGame(gameId)
        if (game == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Game not found"))
            return@post
        }

        if (game.hostPlayerId != player.id) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only the host can start the game"))
            return@post
        }

        if (game.status != "LOBBY") {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Game is not in lobby"))
            return@post
        }

        val playerCount = gameRepo.getGamePlayerCount(gameId)
        if (playerCount < 2) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Need at least 2 players to start"))
            return@post
        }

        // Generate the board and create game state
        val (tiles, ports) = BoardGenerator.generateBoard()
        val gamePlayers = gameRepo.getGamePlayers(gameId)
        val playerModels = gamePlayers.map { gp ->
            val p = playerRepo.findById(gp.playerId)!!
            Player(
                id = gp.playerId,
                displayName = p.displayName,
                color = PlayerColor.valueOf(gp.color)
            )
        }.toMutableList()

        val state = GameState(
            gameId = gameId,
            tiles = tiles,
            ports = ports,
            players = playerModels,
            currentPlayerIndex = 0,
            phase = GamePhase.SETUP_FORWARD,
            robberLocation = tiles.first { it.hasRobber }.coord,
            devCardDeck = BoardGenerator.createDevCardDeck()
        )

        gameRepo.saveGameState(gameId, state)
        gameRepo.updateGameStatus(gameId, "ACTIVE")

        call.respond(HttpStatusCode.OK, mapOf("success" to true))
    }

    get("/games/{gameId}/state") {
        val token = call.request.header("X-Session-Token")
        val player = authenticatePlayer(token)
        if (player == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid session token"))
            return@get
        }

        val gameId = call.parameters["gameId"]!!
        val state = gameRepo.getGameState(gameId)
        if (state == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Game state not found"))
            return@get
        }

        // Filter: hide other players' dev cards
        val filteredState = filterStateForPlayer(state, player.id)
        call.respond(HttpStatusCode.OK, filteredState)
    }
}

/**
 * Returns a copy of the game state with other players' dev cards hidden.
 */
fun filterStateForPlayer(state: GameState, playerId: String): GameState {
    val filteredPlayers = state.players.map { p ->
        if (p.id == playerId) {
            p
        } else {
            p.copy(
                devCards = MutableList(p.devCards.size) { DevelopmentCardType.KNIGHT }, // masked
                newDevCards = mutableListOf()
            )
        }
    }.toMutableList()

    return state.copy(players = filteredPlayers)
}
