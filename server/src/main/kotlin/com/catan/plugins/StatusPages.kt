package com.catan.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String, val message: String)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Bad Request", cause.message ?: "Invalid request"))
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse("Conflict", cause.message ?: "Invalid state"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal Server Error", "An unexpected error occurred"))
        }
    }
}
