package com.catan.plugins

import com.catan.db.GameRepository
import com.catan.db.PlayerRepository
import com.catan.game.GameEngine
import com.catan.routes.gameRoutes
import com.catan.routes.healthRoutes
import com.catan.routes.playerRoutes
import com.catan.ws.GameSessionManager
import com.catan.ws.gameWebSocket
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val playerRepo = PlayerRepository()
    val gameRepo = GameRepository()
    val sessionManager = GameSessionManager()
    val engine = GameEngine()

    // Reload active games from DB on startup
    gameRepo.loadActiveGamesFromDb()

    routing {
        healthRoutes()
        playerRoutes(playerRepo)
        gameRoutes(gameRepo, playerRepo)
        gameWebSocket(gameRepo, playerRepo, sessionManager, engine)
    }
}
