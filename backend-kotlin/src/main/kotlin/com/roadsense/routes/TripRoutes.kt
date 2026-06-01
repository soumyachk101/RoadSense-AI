package com.roadsense.routes

import com.roadsense.db.PocCandidates
import com.roadsense.db.Trips
import com.roadsense.models.BatchPocRequest
import com.roadsense.models.CreateTripRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

fun Route.tripRoutes() {
    authenticate("auth-jwt") {
        route("/trips") {
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.subject ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val req = call.receive<CreateTripRequest>()
                val tripId = transaction {
                    Trips.insertAndGetId {
                        it[Trips.userId] = UUID.fromString(userId)
                        it[vehicleType] = req.vehicle_type
                        it[phonePlacement] = req.phone_placement
                        it[status] = "active"
                        it[startedAt] = LocalDateTime.now()
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to tripId.toString()))
            }

            post("/{id}/end") {
                val tripId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                transaction {
                    Trips.update({ Trips.id eq UUID.fromString(tripId) }) {
                        it[status] = "processing"
                        it[endedAt] = LocalDateTime.now()
                    }
                }
                
                // In a real app, this would be a background job (e.g., via RabbitMQ or BullMQ equivalent)
                // For this migration, we'll call the processor directly
                com.roadsense.services.TripProcessor.processTrip(UUID.fromString(tripId))
                
                call.respond(HttpStatusCode.OK, mapOf("status" to "processing"))
            }

            post("/{id}/poc") {
                val tripId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val req = call.receive<BatchPocRequest>()
                
                transaction {
                    PocCandidates.batchInsert(req.candidates) { c ->
                        this[PocCandidates.tripId] = UUID.fromString(tripId)
                        this[PocCandidates.lat] = c.lat
                        this[PocCandidates.lng] = c.lng
                        this[PocCandidates.zValue] = c.z_value
                        this[PocCandidates.zNext] = c.z_next
                        this[PocCandidates.zPrev] = c.z_prev
                        this[PocCandidates.tp] = c.tp
                        this[PocCandidates.speedKmh] = c.speed_kmh
                        this[PocCandidates.thresholdUsed] = c.threshold_used
                        // recordedAt conversion simplified for now
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("inserted" to req.candidates.size))
            }
        }
    }
}
