package com.catan.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val service: String, val timestamp: Long = System.currentTimeMillis())

fun Routing.healthRoutes() {
    get("/health") {
        call.respond(HttpStatusCode.OK, HealthResponse(status = "ok", service = "hexagon-trading-game"))
    }
}
