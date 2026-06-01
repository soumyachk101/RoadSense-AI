package com.roadsense.services

import com.roadsense.db.PocCandidates
import com.roadsense.db.RoadEvents
import com.roadsense.db.Trips
import com.roadsense.ml.PocCandidate
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.*

object TripProcessor {
    suspend fun processTrip(tripId: UUID) {
        val pocs = transaction {
            PocCandidates.select { PocCandidates.tripId eq tripId }.map {
                PocCandidate(
                    z_value = it[PocCandidates.zValue],
                    z_next = it[PocCandidates.zNext],
                    z_prev = it[PocCandidates.zPrev],
                    tp = it[PocCandidates.tp] ?: 0.0,
                    speed_kmh = it[PocCandidates.speedKmh] ?: 0.0,
                    lat = it[PocCandidates.lat],
                    lng = it[PocCandidates.lng]
                ) to it[PocCandidates.id].value
            }
        }

        if (pocs.isEmpty()) {
            transaction {
                Trips.update({ Trips.id eq tripId }) {
                    it[status] = "completed"
                }
            }
            return
        }

        val mlResults = MLClient.classify(pocs.map { it.first })

        transaction {
            pocs.forEachIndexed { index, pair ->
                val (poc, pocId) = pair
                val mlResult = mlResults?.getOrNull(index)

                val eventType = mlResult?.event_type ?: if (poc.z_value > 0) "speed_breaker" else "pothole"
                val confidence = mlResult?.confidence ?: 0.6
                val severity = if (Math.abs(poc.z_value) > 2.0) "HIGH" else "MEDIUM"

                RoadEvents.insert {
                    it[RoadEvents.tripId] = tripId
                    it[pocCandidateId] = pocId
                    it[RoadEvents.eventType] = eventType
                    it[lat] = poc.lat
                    it[lng] = poc.lng
                    it[RoadEvents.severity] = severity
                    it[confidenceScore] = confidence
                }
            }

            Trips.update({ Trips.id eq tripId }) {
                it[status] = "completed"
            }
        }
    }
}
