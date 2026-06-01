package com.roadsense.db

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Users : UUIDTable("users") {
    val phone = varchar("phone", 20).uniqueIndex()
    val name = varchar("name", 100).nullable()
    val role = varchar("role", 20).default("user")
    val vehicleType = varchar("vehicle_type", 30).nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
}

object Trips : UUIDTable("trips") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val vehicleType = varchar("vehicle_type", 30).nullable()
    val phonePlacement = varchar("phone_placement", 30).nullable()
    val status = varchar("status", 20).default("pending")
    val rawDataPath = text("raw_data_path").nullable()
    val startedAt = datetime("started_at").nullable()
    val endedAt = datetime("ended_at").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}

object PocCandidates : UUIDTable("poc_candidates") {
    val tripId = reference("trip_id", Trips, onDelete = ReferenceOption.CASCADE)
    val lat = double("lat")
    val lng = double("lng")
    val zValue = double("z_value")
    val zNext = double("z_next").nullable()
    val zPrev = double("z_prev").nullable()
    val tp = double("tp").nullable()
    val speedKmh = double("speed_kmh").nullable()
    val thresholdUsed = double("threshold_used").nullable()
    val recordedAt = datetime("recorded_at").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}

object RoadEvents : UUIDTable("road_events") {
    val tripId = reference("trip_id", Trips, onDelete = ReferenceOption.CASCADE).nullable()
    val pocCandidateId = reference("poc_candidate_id", PocCandidates, onDelete = ReferenceOption.SET_NULL).nullable()
    val eventType = varchar("event_type", 30)
    val lat = double("lat")
    val lng = double("lng")
    val severity = varchar("severity", 20).nullable()
    val confidenceScore = double("confidence_score").nullable()
    val isConfirmed = bool("is_confirmed").default(false)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}

object ConfirmedEvents : UUIDTable("confirmed_events") {
    val eventType = varchar("event_type", 30)
    val lat = double("lat")
    val lng = double("lng")
    val trailCount = integer("trail_count").default(1)
    val confidenceScore = double("confidence_score").nullable()
    val firstSeen = datetime("first_seen").nullable()
    val lastSeen = datetime("last_seen").nullable()
    val isActive = bool("is_active").default(true)
    val resolvedAt = datetime("resolved_at").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}

object EventClusterMembers : UUIDTable("event_cluster_members") {
    val confirmedEventId = reference("confirmed_event_id", ConfirmedEvents, onDelete = ReferenceOption.CASCADE)
    val roadEventId = reference("road_event_id", RoadEvents, onDelete = ReferenceOption.CASCADE)
    
    // Composite PK in Exposed is tricky with UUIDTable, but we can override or use a helper
    override val primaryKey = PrimaryKey(confirmedEventId, roadEventId)
}
