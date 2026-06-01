package com.roadsense.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureSecurity() {
    val jwtSecret = System.getenv("JWT_SECRET") ?: "secret"
    val jwtIssuer = System.getenv("JWT_ISSUER") ?: "roadsense.com"
    val jwtAudience = System.getenv("JWT_AUDIENCE") ?: "roadsense-users"
    val jwtRealm = System.getenv("JWT_REALM") ?: "RoadSense Access"

    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwtRealm
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }
}
