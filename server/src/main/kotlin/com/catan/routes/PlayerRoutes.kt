package com.catan.routes

import com.catan.db.PlayerRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val displayName: String)

@Serializable
data class RegisterResponse(val playerId: String, val sessionToken: String, val displayName: String)

fun Routing.playerRoutes(playerRepo: PlayerRepository) {
    post("/players/register") {
        val request = call.receive<RegisterRequest>()
        if (request.displayName.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "displayName is required"))
            return@post
        }
        if (request.displayName.length > 50) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "displayName must be 50 characters or less"))
            return@post
        }
        val player = playerRepo.createPlayer(request.displayName.trim())
        call.respond(HttpStatusCode.OK, RegisterResponse(player.id, player.sessionToken, player.displayName))
    }
}
