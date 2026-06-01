package com.roadsense.routes

import com.roadsense.db.ConfirmedEvents
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.serialization.Serializable

@Serializable
data class EventResponse(
    val id: String,
    val event_type: String,
    val lat: Double,
    val lng: Double,
    val trail_count: Int,
    val confidence_score: Double?,
    val is_active: Boolean
)

fun Route.eventRoutes() {
    route("/events") {
        get("/nearby") {
            val lat = call.parameters["lat"]?.toDoubleOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
            val lng = call.parameters["lng"]?.toDoubleOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
            val radius = call.parameters["radius"]?.toDoubleOrNull() ?: 5000.0 // meters

            val results = transaction {
                // Simple bounding box filtering as a proxy for PostGIS/spatial query
                val latDelta = radius / 111000.0
                val lngDelta = radius / (111000.0 * Math.cos(Math.toRadians(lat)))

                ConfirmedEvents.select {
                    (ConfirmedEvents.lat greaterEq (lat - latDelta)) and
                    (ConfirmedEvents.lat lessEq (lat + latDelta)) and
                    (ConfirmedEvents.lng greaterEq (lng - lngDelta)) and
                    (ConfirmedEvents.lng lessEq (lng + lngDelta)) and
                    (ConfirmedEvents.isActive eq true)
                }.map {
                    EventResponse(
                        id = it[ConfirmedEvents.id].value.toString(),
                        event_type = it[ConfirmedEvents.eventType],
                        lat = it[ConfirmedEvents.lat],
                        lng = it[ConfirmedEvents.lng],
                        trail_count = it[ConfirmedEvents.trailCount],
                        confidence_score = it[ConfirmedEvents.confidenceScore],
                        is_active = it[ConfirmedEvents.isActive]
                    )
                }
            }
            call.respond(results)
        }
    }
}
