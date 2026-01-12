package com.kmpforge.debugforge.server

import com.kmpforge.debugforge.api.*
import com.kmpforge.debugforge.core.*
import com.kmpforge.debugforge.persistence.*
import com.kmpforge.debugforge.state.*
import com.kmpforge.debugforge.sync.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.io.File

/**
 * Embedded server for DebugForge that can be started within each app.
 */

class DebugForgeEmbeddedServer(
    private val port: Int = 18999,
    private val host: String = "127.0.0.1",
    private val groqApiKey: String? = null,
    private val githubToken: String? = null
)
{

    private var server: NettyApplicationEngine? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isServerRunning = false

    fun start() {
        if (isServerRunning) {
            println("DEBUG: Server already running, skipping start")
            return
        }

        println("DEBUG: Creating embedded server on $host:$port")
        server = embeddedServer(Netty, port = port, host = host) {
            configureServer(scope, groqApiKey, githubToken)
        }

        try {
            println("DEBUG: Starting embedded server...")
            server?.start(wait = false) // Start asynchronously
            // Give it a moment to start
            Thread.sleep(200)
            
            // Check if server is actually listening on the port
            val socket = java.net.Socket()
            try {
                socket.connect(java.net.InetSocketAddress(host, port), 1000)
                socket.close()
                isServerRunning = true
                println("DEBUG: Embedded server started successfully and is listening on $host:$port")
            } catch (e: Exception) {
                println("DEBUG: Server not listening on port $port: ${e.message}")
                isServerRunning = false
                server?.stop()
                server = null
                throw RuntimeException("Server failed to bind to port $port", e)
            }
        } catch (e: Exception) {
            println("DEBUG: Failed to start server: ${e.message}")
            e.printStackTrace()
            isServerRunning = false
            throw e
        }
    }

    fun stop() {
        server?.stop()
        scope.cancel()
        isServerRunning = false
    }

    /**
     * Check if server is running
     */
    fun isRunning(): Boolean {
        val running = isServerRunning && server != null
        println("DEBUG: isRunning called, isServerRunning=$isServerRunning, server!=null=${server != null}, running=$running")
        return running
    }
}

/**
 * Configures the Ktor server with all necessary plugins and routes.
 * This is shared between standalone server and embedded server.
 */
fun Application.configureServer(scope: CoroutineScope, groqApiKey: String? = null, githubToken: String? = null) {
    // Install plugins
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost() // Allow all hosts for embedded usage
    }

    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // condition
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    // Initialize dependencies
    val dao = InMemoryRepoIndexDao()
    val fileSystem = JvmFileSystem()
    val gitOperations = JvmGitOperations()

    val controller = DebugForgeControllerFactory.create(dao, fileSystem, gitOperations)

    // HTTP Client for external APIs
    val httpClient = HttpClient()

    routing {
        // Health check
        get("/health") {
            call.respond(mapOf("status" to "ok", "version" to "1.0.0"))
        }

        post("/repo/clone") {
            try {
                val request = call.receive<CloneRepoRequest>()
                scope.launch {
                    controller.cloneRepository(request.url, request.localPath)
                }
                call.respond(CloneRepoResponse(
                    success = true,
                    message = "Repository clone started",
                    repoId = request.localPath
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, CloneRepoResponse(
                    success = false,
                    message = "Failed to start repository clone",
                    error = e.message
                ))
            }
        }

        post("/repo/load") {
            try {
                val request = call.receive<LoadRepoRequest>()
                scope.launch {
                    controller.loadRepository(request.path)
                }
                call.respond(LoadRepoResponse(
                    success = true,
                    message = "Repository load started",
                    repoId = request.path
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, LoadRepoResponse(
                    success = false,
                    message = "Failed to start repository load",
                    error = e.message
                ))
            }
        }

        // Analysis operations
        post("/analyze") {
            try {
                val repoId = call.parameters["repoId"] ?: return@post call.respond(HttpStatusCode.BadRequest, "repoId required")
                // Analysis is handled automatically by the controller when repository is loaded
                call.respond(mapOf("status" to "analysis_not_needed"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        get("/analysis/{repoId}") {
            try {
                val repoId = call.parameters["repoId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "repoId required")
                // Return current state from controller
                val state = controller.state.value
                call.respond(state)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }

        // AI Analysis
        post("/ai/analyze") {
            val aiRequest = call.receive<AIAnalyzeRequest>()

            val key = groqApiKey ?: System.getenv("GROQ_API_KEY") ?: ""

            if (key.isEmpty()) {
                call.respond(HttpStatusCode.OK, AIAnalyzeResponse(
                    success = false,
                    analysis = "{}",
                    model = "",
                    error = "AI service not configured. Set API key in settings."
                ))
                return@post
            }

            try {
                // Read code from file if filePath provided
                val code = if (aiRequest.code.isNotEmpty()) {
                    aiRequest.code
                } else if (aiRequest.filePath.isNotEmpty()) {
                    File(aiRequest.filePath).readText()
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Provide code or filePath"))
                    return@post
                }

                val prompt = """
Analyze this Kotlin code and identify issues. Focus on:
- Performance problems
- Memory leaks (especially coroutine scope leaks)
- Incorrect API usage
- Threading issues
- Platform-specific problems in KMP

File: ${aiRequest.fileName}
Context: ${aiRequest.context}

Code:
```kotlin
$code
```

Return a JSON object with this exact format:
{
  "suggestions": [
    {
      "title": "Short title",
      "rationale": "Why this is an issue and how to fix it",
      "beforeCode": "problematic code snippet",
      "afterCode": "fixed code snippet",
      "confidence": 0.8
    }
  ],
  "summary": "Brief summary of findings"
}
"""

                // Call Groq API
                val client = HttpClient()
                val requestBody = """
{
  "model": "mixtral-8x7b-32768",
  "messages": [
    {
      "role": "user",
      "content": "$prompt"
    }
  ],
  "temperature": 0.1,
  "max_tokens": 2048
}
"""

                val response = client.post("https://api.groq.com/openai/v1/chat/completions") {
                    header("Authorization", "Bearer $key")
                    header("Content-Type", "application/json")
                    setBody(requestBody)
                }

                val responseText = response.bodyAsText()
                val jsonResponse = Json.parseToJsonElement(responseText)

                // Extract the analysis from the response
                val choices = jsonResponse.jsonObject["choices"]?.jsonArray
                val analysis = choices?.get(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: "{}"

                call.respond(AIAnalyzeResponse(
                    success = true,
                    analysis = analysis,
                    model = "mixtral-8x7b-32768"
                ))

            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, AIAnalyzeResponse(
                    success = false,
                    analysis = "{}",
                    model = "",
                    error = e.message
                ))
            }
        }

        // Error handling
        post("/error/clear") {
            controller.clearError()
            call.respond(HttpStatusCode.OK, mapOf("status" to "cleared"))
        }
    }
}