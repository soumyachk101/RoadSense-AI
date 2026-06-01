package com.roadsense

import com.roadsense.ml.Classifier
import com.roadsense.ml.Clustering
import com.roadsense.ml.PocCandidate
import com.roadsense.ml.Event
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun main() {
    embeddedServer(Netty, port = 8001, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    
    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok", "service" to "roadsense-ml-kotlin"))
        }

        post("/classify") {
            val req = call.receive<ClassifyRequest>()
            val results = req.candidates.map { Classifier.classify(it) }
            call.respond(mapOf("results" to results, "count" to results.size))
        }

        post("/cluster") {
            val req = call.receive<ClusterRequest>()
            val confirmed = Clustering.cluster(req.events, req.total_trails)
            call.respond(mapOf("confirmed" to confirmed, "count" to confirmed.size))
        }
    }
}

@Serializable
data class ClassifyRequest(val candidates: List<PocCandidate>)

@Serializable
data class ClusterRequest(val events: List<Event>, val total_trails: Int)
