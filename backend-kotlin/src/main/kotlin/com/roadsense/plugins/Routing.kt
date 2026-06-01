package com.roadsense.plugins

import com.roadsense.routes.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        authRoutes()
        tripRoutes()
        leaderboardRoutes()
        eventRoutes()
    }
}
