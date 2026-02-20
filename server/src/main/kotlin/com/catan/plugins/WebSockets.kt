package com.catan.plugins

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds

fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}
