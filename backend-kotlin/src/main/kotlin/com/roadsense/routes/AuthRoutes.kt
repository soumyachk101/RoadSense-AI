package com.roadsense.routes

import com.roadsense.db.Users
import com.roadsense.models.LoginRequest
import com.roadsense.models.RegisterRequest
import com.roadsense.models.TokenPair
import com.roadsense.services.TokenService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import java.util.*

fun Route.authRoutes() {
    route("/auth") {
        post("/register") {
            val req = call.receive<RegisterRequest>()
            val userId = transaction {
                Users.upsert(Users.phone) {
                    it[phone] = req.phone
                    if (req.name != null) it[name] = req.name
                    if (req.vehicle_type != null) it[vehicleType] = req.vehicle_type
                }[Users.id]
            }
            call.respond(HttpStatusCode.Created, mapOf("id" to userId.toString()))
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            // Mock OTP verification for now
            if (req.otp != "123456") {
                call.respond(HttpStatusCode.Unauthorized, mapOf("detail" to "Invalid OTP"))
                return@post
            }

            val user = transaction {
                Users.select { Users.phone eq req.phone }.singleOrNull()
            } ?: run {
                call.respond(HttpStatusCode.NotFound, mapOf("detail" to "User not found"))
                return@post
            }

            val tokens = TokenService.generateTokenPair(user[Users.id].value.toString())
            call.respond(tokens)
        }
    }
}
