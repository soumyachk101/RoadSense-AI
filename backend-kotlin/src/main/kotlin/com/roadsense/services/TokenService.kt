package com.roadsense.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.roadsense.models.TokenPair
import java.util.*

object TokenService {
    private val jwtSecret = System.getenv("JWT_SECRET") ?: "secret"
    private val jwtIssuer = System.getenv("JWT_ISSUER") ?: "roadsense.com"
    private val jwtAudience = System.getenv("JWT_AUDIENCE") ?: "roadsense-users"

    fun generateTokenPair(userId: String): TokenPair {
        val algorithm = Algorithm.HMAC256(jwtSecret)
        
        val accessToken = JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withSubject(userId)
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000)) // 1 hour
            .sign(algorithm)

        val refreshToken = JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withSubject(userId)
            .withExpiresAt(Date(System.currentTimeMillis() + 86400000 * 30)) // 30 days
            .sign(algorithm)

        return TokenPair(accessToken, refreshToken)
    }
}
