package com.roadsense.routes

import com.roadsense.db.RoadEvents
import com.roadsense.db.Trips
import com.roadsense.db.Users
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.serialization.Serializable

@Serializable
data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    val events_detected: Long,
    val trips_completed: Long
)

fun Route.leaderboardRoutes() {
    authenticate("auth-jwt") {
        get("/leaderboard") {
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
            
            val results = transaction {
                val eventCount = RoadEvents.tripId.count()
                val tripCount = Trips.id.count()
                
                Users
                    .leftJoin(Trips)
                    .leftJoin(RoadEvents)
                    .slice(Users.name, eventCount, tripCount)
                    .selectAll()
                    .groupBy(Users.id, Users.name)
                    .orderBy(eventCount, SortOrder.DESC)
                    .limit(limit)
                    .mapIndexed { index, row ->
                        LeaderboardEntry(
                            rank = index + 1,
                            name = row[Users.name] ?: "Anonymous",
                            events_detected = row[eventCount],
                            trips_completed = row[tripCount]
                        )
                    }
            }
            
            call.respond(results)
        }
    }
}
