package com.roadsense

import com.roadsense.db.DatabaseFactory
import com.roadsense.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8000, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()
    configureSerialization()
    configureCORS()
    configureSecurity()
    configureRouting()
    configureStatusPages()
}
