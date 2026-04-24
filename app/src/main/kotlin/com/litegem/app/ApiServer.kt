package com.litegem.app

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ollama-compatible HTTP server on localhost:11434
 * Any client that talks to Ollama can talk to liteGem.
 *
 * Endpoints:
 *   GET  /api/tags          → list loaded model
 *   POST /api/generate      → generate completion
 *   POST /api/chat          → chat completion
 *   GET  /api/status        → liteGem engine status
 *   POST /api/load          → load a model by path
 */
class ApiServer(private val engine: InferenceEngine) {

    private var server: NettyApplicationEngine? = null

    fun start(port: Int = 11434) {
        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; prettyPrint = false })
            }
            routing { setupRoutes() }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(500, 1000)
    }

    // -------------------------------------------------------------------------
    // Routes
    // -------------------------------------------------------------------------

    private fun Routing.setupRoutes() {

        // Health check
        get("/") {
            call.respondText("liteGem is running", ContentType.Text.Plain)
        }

        // Ollama: list models
        get("/api/tags") {
            val status = engine.status()
            call.respond(TagsResponse(
                models = if (status["model"] != "none") listOf(
                    ModelInfo(
                        name = status["model"] ?: "unknown",
                        size = 0L,
                        details = ModelDetails(family = status["backend"] ?: "unknown")
                    )
                ) else emptyList()
            ))
        }

        // liteGem: load model
        post("/api/load") {
            val req = call.receive<LoadRequest>()
            val result = engine.loadModel(req.path)
            result.fold(
                onSuccess = { backend ->
                    call.respond(mapOf("status" to "ok", "backend" to backend.name))
                },
                onFailure = { e ->
                    call.respond(HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Unknown error")))
                }
            )
        }

        // Ollama: generate
        post("/api/generate") {
            val req = call.receive<GenerateRequest>()
            val result = engine.generate(req.prompt, req.options?.numPredict ?: 512)
            result.fold(
                onSuccess = { text ->
                    call.respond(GenerateResponse(
                        model = req.model,
                        response = text,
                        done = true
                    ))
                },
                onFailure = { e ->
                    call.respond(HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Generation failed")))
                }
            )
        }

        // Ollama: chat
        post("/api/chat") {
            val req = call.receive<ChatRequest>()
            // Flatten messages into a single prompt for Phase 1
            val prompt = req.messages.joinToString("\n") { msg ->
                "${msg.role.replaceFirstChar { it.uppercase() }}: ${msg.content}"
            } + "\nAssistant:"

            val result = engine.generate(prompt, 512)
            result.fold(
                onSuccess = { text ->
                    call.respond(ChatResponse(
                        model = req.model,
                        message = ChatMessage(role = "assistant", content = text),
                        done = true
                    ))
                },
                onFailure = { e ->
                    call.respond(HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Chat failed")))
                }
            )
        }

        // liteGem status
        get("/api/status") {
            call.respond(engine.status())
        }
    }
}

// -------------------------------------------------------------------------
// Request / Response models
// -------------------------------------------------------------------------

@Serializable data class LoadRequest(val path: String)

@Serializable data class GenerateRequest(
    val model: String = "litegem",
    val prompt: String,
    val stream: Boolean = false,
    val options: GenerateOptions? = null
)
@Serializable data class GenerateOptions(val numPredict: Int = 512)
@Serializable data class GenerateResponse(
    val model: String,
    val response: String,
    val done: Boolean
)

@Serializable data class ChatRequest(
    val model: String = "litegem",
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)
@Serializable data class ChatMessage(val role: String, val content: String)
@Serializable data class ChatResponse(
    val model: String,
    val message: ChatMessage,
    val done: Boolean
)

@Serializable data class TagsResponse(val models: List<ModelInfo>)
@Serializable data class ModelInfo(val name: String, val size: Long, val details: ModelDetails)
@Serializable data class ModelDetails(val family: String)
