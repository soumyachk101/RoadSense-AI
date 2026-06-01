package com.roadsense.ml

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class Event(
    val lat: Double,
    val lng: Double,
    val event_type: String,
    val trip_id: String? = null
)

object Clustering {
    private const val EARTH_RADIUS = 6371000.0 // meters

    fun cluster(events: List<Event>, totalTrails: Int): List<Event> {
        if (events.isEmpty()) return emptyList()

        // Very simple spatial clustering: group events within 5 meters
        val clusters = mutableListOf<MutableList<Event>>()
        
        for (event in events) {
            var added = false
            for (cluster in clusters) {
                val centroid = cluster.first() // Use first as representative
                if (distance(event.lat, event.lng, centroid.lat, centroid.lng) < 5.0) {
                    cluster.add(event)
                    added = true
                    break
                }
            }
            if (!added) {
                clusters.add(mutableListOf(event))
            }
        }

        // Filter clusters that meet the consensus threshold: ceil(N_T/3) + 1
        val threshold = ceil(totalTrails / 3.0).toInt() + 1
        return clusters.filter { it.size >= threshold }.map { cluster ->
            // Return a representative event (medoid-ish)
            cluster.first()
        }
    }

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS * c
    }
}
