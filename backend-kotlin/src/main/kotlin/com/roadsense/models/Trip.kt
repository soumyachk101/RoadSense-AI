package com.roadsense.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateTripRequest(
    val vehicle_type: String,
    val phone_placement: String
)

@Serializable
data class PocCandidateRequest(
    val lat: Double,
    val lng: Double,
    val z_value: Double,
    val z_next: Double? = null,
    val z_prev: Double? = null,
    val tp: Double? = null,
    val speed_kmh: Double? = null,
    val threshold_used: Double? = null,
    val recorded_at: String? = null
)

@Serializable
data class BatchPocRequest(
    val candidates: List<PocCandidateRequest>
)
