package com.roadsense.services

import com.roadsense.ml.ClassificationResult
import com.roadsense.ml.PocCandidate
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable

@Serializable
data class ClassifyRequest(val candidates: List<PocCandidate>)

@Serializable
data class ClassifyResponse(val results: List<ClassificationResult>)

object MLClient {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }
    private val mlUrl = System.getenv("ML_SERVICE_URL") ?: "http://localhost:8001"

    suspend fun classify(candidates: List<PocCandidate>): List<ClassificationResult>? {
        return try {
            val response: ClassifyResponse = client.post("$mlUrl/classify") {
                contentType(ContentType.Application.Json)
                setBody(ClassifyRequest(candidates))
            }.body()
            response.results
        } catch (e: Exception) {
            println("ML Service error: ${e.message}")
            null
        }
    }
}
