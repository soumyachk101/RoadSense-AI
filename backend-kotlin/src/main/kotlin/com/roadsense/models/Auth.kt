package com.roadsense.models

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val phone: String,
    val name: String? = null,
    val vehicle_type: String? = null
)

@Serializable
data class LoginRequest(
    val phone: String,
    val otp: String
)

@Serializable
data class TokenPair(
    val access_token: String,
    val refresh_token: String
)
