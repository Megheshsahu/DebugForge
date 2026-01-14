package com.kmpforge.debugforge.server

import com.kmpforge.debugforge.core.*
import com.kmpforge.debugforge.persistence.*
import com.kmpforge.debugforge.state.*
import com.kmpforge.debugforge.sync.*
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
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Main entry point for the DebugForge backend server.
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8765
    val host = System.getenv("HOST") ?: "127.0.0.1"
    
    embeddedServer(Netty, port = port, host = host) {
        configureServer()
    }.start(wait = true)
}

/**
 * Configures the Ktor server with all necessary plugins and routes.
 */
fun Application.configureServer() {
    // Initialize components
    val databasePath = DatabaseDriverFactory.getDefaultDatabasePath()
    val driver = DatabaseDriverFactory.createDriver(databasePath)
    
    // Create DAO using in-memory implementation for now
    // TODO: Replace with SQLDelight-backed implementation when schema is generated
    val dao = InMemoryRepoIndexDao()
    
    val fileSystem = JvmFileSystem()
    val gitOperations = JvmGitOperations()
    
    val controller = DebugForgeControllerFactory.create(
        dao = dao,
        fileSystem = fileSystem,
        gitOperations = gitOperations
    )
    
    // Configure plugins
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost() // For local development - restrict in production
    }
    
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 0.9
        }
    }
    
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    code = "INTERNAL_ERROR",
                    message = cause.message ?: "An unexpected error occurred"
                )
            )
        }
    }
    
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    
    // Configure routes
    routing {
        // Health check
        get("/health") {
            call.respond(mapOf("status" to "ok", "version" to "1.0.0"))
        }
        
        // API routes
        route("/api") {
            /**
             * Sync a fix to GitHub (create branch, commit, PR)
             * Request: { owner, repo, filePath, newContent, fixDescription }
             * Response: { status, prNumber, prUrl, branch, error }
             */
            post("/github/sync") {
                // Parse request
                val req = call.receive<GitHubPRRequest>()

                // Read GitHub token from env
                val githubToken = System.getenv("GITHUB_TOKEN") ?: ""
                if (githubToken.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("status" to "error", "error" to "GitHub token not configured on server"))
                    return@post
                }

                // Create Ktor HttpClient
                val httpClient = io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true; prettyPrint = true })
                    }
                }

                // Create GitHubService and SyncManager
                val githubService = com.kmpforge.debugforge.sync.GitHubService(githubToken, httpClient)
                val syncManager = com.kmpforge.debugforge.sync.SyncManager(githubService)

                // Run sync in coroutine
                val syncResult = try {
                    kotlinx.coroutines.runBlocking {
                        syncManager.applyAndSync(
                            owner = req.owner,
                            repo = req.repo,
                            filePath = req.filePath,
                            newContent = req.newContent,
                            fixDescription = req.fixDescription
                        )
                    }
                } catch (e: Exception) {
                    com.kmpforge.debugforge.sync.SyncResult.Failed("Exception: ${e.message}")
                }

                when (syncResult) {
                    is com.kmpforge.debugforge.sync.SyncResult.Success -> call.respond(mapOf(
                        "status" to "success",
                        "prNumber" to syncResult.prNumber,
                        "prUrl" to syncResult.prUrl,
                        "branch" to syncResult.branch
                    ))
                    is com.kmpforge.debugforge.sync.SyncResult.Failed -> call.respond(mapOf(
                        "status" to "error",
                        "error" to syncResult.error
                    ))
                }
            }
            // State endpoint
            get("/state") {
                call.respond(controller.state.value)
            }
            
            // Load repository from local path
            post("/repo/load") {
                val request = call.receive<LoadRepoRequest>()
                
                launch {
                    controller.loadRepository(request.path)
                }
                
                call.respond(HttpStatusCode.Accepted, mapOf("status" to "loading"))
            }
            
            // Clone repository from URL
            post("/repo/clone") {
                val request = call.receive<CloneRepoRequest>()
                
                launch {
                    controller.cloneRepository(request.url, request.localPath)
                }
                
                call.respond(HttpStatusCode.Accepted, mapOf("status" to "cloning"))
            }
            
            // Refresh analysis
            post("/repo/refresh") {
                launch {
                    controller.refresh()
                }
                
                call.respond(HttpStatusCode.Accepted, mapOf("status" to "refreshing"))
            }
            
            // Get modules
            get("/modules") {
                call.respond(controller.state.value.modules)
            }
            
            // Get diagnostics
            get("/diagnostics") {
                val filter = call.request.queryParameters.let { params ->
                    DiagnosticFilterParams(
                        severities = params.getAll("severity") ?: emptyList(),
                        categories = params.getAll("category") ?: emptyList(),
                        moduleIds = params.getAll("module") ?: emptyList(),
                        sourceSets = params.getAll("sourceSet") ?: emptyList(),
                        search = params["search"] ?: ""
                    )
                }
                
                val diagnosticFilter = com.kmpforge.debugforge.diagnostics.DiagnosticFilter(
                    severities = filter.severities.mapNotNull { 
                        runCatching { 
                            com.kmpforge.debugforge.diagnostics.DiagnosticSeverity.valueOf(it.uppercase()) 
                        }.getOrNull() 
                    }.toSet(),
                    categories = filter.categories.mapNotNull { 
                        runCatching { 
                            com.kmpforge.debugforge.diagnostics.DiagnosticCategory.valueOf(it.uppercase()) 
                        }.getOrNull() 
                    }.toSet(),
                    modules = filter.moduleIds.toSet(),
                    filePattern = filter.search
                )
                
                call.respond(controller.getFilteredDiagnostics(diagnosticFilter))
            }
            
            // Suppress diagnostic
            post("/diagnostics/{id}/suppress") {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing diagnostic ID")
                val request = call.receiveOrNull<SuppressRequest>()
                
                controller.suppressDiagnostic(id, request?.reason ?: "User suppressed")
                
                call.respond(HttpStatusCode.OK, mapOf("status" to "suppressed"))
            }
            
            // Get refactoring suggestions
            get("/refactors") {
                call.respond(controller.state.value.refactorSuggestions)
            }
            
            // Apply refactoring
            post("/refactors/{id}/apply") {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing refactor ID")
                
                val success = controller.applyRefactoring(id)
                
                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "applied"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "status" to "failed",
                        "message" to "Refactoring could not be applied automatically"
                    ))
                }
            }
            
            // Dismiss refactoring
            post("/refactors/{id}/dismiss") {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing refactor ID")
                val request = call.receiveOrNull<DismissRequest>()
                
                controller.dismissRefactoring(id, request?.reason ?: "User dismissed")
                
                call.respond(HttpStatusCode.OK, mapOf("status" to "dismissed"))
            }
            
            // Get shared code metrics
            get("/metrics") {
                call.respond(controller.state.value.sharedCodeMetrics)
            }
            
            // Get previews
            get("/previews") {
                call.respond(controller.state.value.previews)
            }
            
            // Start preview
            post("/previews/{id}/start") {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("Missing preview ID")
                val request = call.receive<StartPreviewRequest>()
                
                val platform = SourceSetPlatform.valueOf(request.platform.uppercase())
                val session = controller.startPreview(id, platform)
                
                if (session != null) {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "started",
                        "sessionId" to session.id
                    ))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "status" to "error",
                        "message" to "Preview not found"
                    ))
                }
            }
            
            // Stop preview
            post("/previews/{sessionId}/stop") {
                val sessionId = call.parameters["sessionId"] 
                    ?: throw IllegalArgumentException("Missing session ID")
                
                controller.stopPreview(sessionId)
                
                call.respond(HttpStatusCode.OK, mapOf("status" to "stopped"))
            }
            
            // Notify file change
            post("/files/changed") {
                val request = call.receive<FileChangedRequest>()
                
                controller.notifyFileChanged(request.path)
                
                call.respond(HttpStatusCode.OK, mapOf("status" to "acknowledged"))
            }
            
            // Clear error
            post("/error/clear") {
                controller.clearError()
                call.respond(HttpStatusCode.OK, mapOf("status" to "cleared"))
            }
            
            // AI-powered code analysis using xAI (Grok)
            post("/ai/analyze") {
                val aiRequest = call.receive<AIAnalyzeRequest>()
                
                // Groq API key - MUST be set via environment variable
                val groqApiKey = System.getenv("GROQ_API_KEY") ?: ""
                
                if (groqApiKey.isEmpty()) {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "error",
                        "analysis" to "{}",
                        "model" to "",
                        "error" to "AI service not configured. Set GROQ_API_KEY environment variable."
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
      "confidence": 0.95
    }
  ],
  "summary": "Brief analysis summary"
}
""".trimIndent()

                    // Build JSON body for Groq API (manually to avoid serialization issues)
                    val escapedPrompt = prompt.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t")
                    
                    val requestBodyJson = """
                        {
                            "model": "llama-3.3-70b-versatile",
                            "messages": [
                                {"role": "system", "content": "You are an expert Kotlin/KMP code analyzer. Return only valid JSON."},
                                {"role": "user", "content": "$escapedPrompt"}
                            ],
                            "temperature": 0.1,
                            "max_tokens": 2048
                        }
                    """.trimIndent()
                    
                    // Use Java HttpClient to call Groq API
                    val javaClient = HttpClient.newHttpClient()
                    val httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                        .header("Authorization", "Bearer $groqApiKey")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                        .build()
                    
                    val response = javaClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
                    val responseBody = response.body()
                    
                    // Log response for debugging
                    call.application.environment.log.info("xAI Response Status: ${response.statusCode()}")
                    call.application.environment.log.info("xAI Response Body (first 500 chars): ${responseBody.take(500)}")
                    
                    // Check for API errors
                    if (response.statusCode() != 200) {
                        val errorJson = Json.parseToJsonElement(responseBody)
                        val errorMessage = errorJson.jsonObject["error"]?.jsonPrimitive?.content 
                            ?: errorJson.jsonObject["code"]?.jsonPrimitive?.content
                            ?: "API returned status ${response.statusCode()}"
                        
                        call.respond(HttpStatusCode.OK, mapOf(
                            "status" to "error",
                            "analysis" to "{}",
                            "model" to "llama-3.3-70b-versatile",
                            "error" to errorMessage
                        ))
                        return@post
                    }
                    
                    // Parse the response
                    val jsonResponse = Json.parseToJsonElement(responseBody)
                    val content = jsonResponse
                        .jsonObject["choices"]
                        ?.jsonArray?.getOrNull(0)
                        ?.jsonObject?.get("message")
                        ?.jsonObject?.get("content")
                        ?.jsonPrimitive?.content ?: "{}"
                    
                    // Extract JSON from content (may have markdown code blocks)
                    val jsonContent = if (content.contains("```json")) {
                        content.substringAfter("```json").substringBefore("```").trim()
                    } else if (content.contains("```")) {
                        content.substringAfter("```").substringBefore("```").trim()
                    } else {
                        content.trim()
                    }
                    
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "success",
                        "analysis" to jsonContent,
                        "model" to "llama-3.3-70b-versatile"
                    ))
                    
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "error",
                        "analysis" to "{}",
                        "model" to "",
                        "error" to "AI analysis failed: ${e.message ?: "Unknown error"}"
                    ))
                }
            }
        }
        
        // WebSocket for real-time updates
        webSocket("/ws") {
            val json = Json { ignoreUnknownKeys = true }
            
            // Send initial state
            val initialState = controller.state.value
            send(Frame.Text(json.encodeToString(DebugForgeState.serializer(), initialState)))
            
            // Collect state changes
            val stateJob = launch {
                controller.state
                    .drop(1) // Skip initial (already sent)
                    .collect { state ->
                        try {
                            send(Frame.Text(json.encodeToString(
                                WebSocketMessage.serializer(),
                                WebSocketMessage(
                                    type = "state",
                                    payload = json.encodeToString(
                                        DebugForgeState.serializer(),
                                        state
                                    )
                                )
                            )))
                        } catch (e: Exception) {
                            // Connection closed
                        }
                    }
            }
            
            // Collect diagnostic events
            val diagnosticJob = launch {
                controller.diagnosticEvents.collect { event ->
                    try {
                        send(Frame.Text(json.encodeToString(
                            WebSocketMessage.serializer(),
                            WebSocketMessage(
                                type = "diagnostic",
                                payload = json.encodeToString(
                                    com.kmpforge.debugforge.diagnostics.DiagnosticEvent.serializer(),
                                    event
                                )
                            )
                        )))
                    } catch (e: Exception) {
                        // Connection closed
                    }
                }
            }
            
            // Handle incoming messages
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val message = json.decodeFromString(WebSocketCommand.serializer(), text)
                        
                        when (message.command) {
                            "load" -> {
                                val path = message.args["path"] ?: continue
                                launch { controller.loadRepository(path) }
                            }
                            "clone" -> {
                                val url = message.args["url"] ?: continue
                                val localPath = message.args["localPath"] ?: continue
                                launch { controller.cloneRepository(url, localPath) }
                            }
                            "refresh" -> {
                                launch { controller.refresh() }
                            }
                            "ping" -> {
                                send(Frame.Text("""{"type":"pong"}"""))
                            }
                        }
                    }
                }
            } finally {
                stateJob.cancel()
                diagnosticJob.cancel()
            }
        }
    }
    
    // Cleanup on shutdown
    environment.monitor.subscribe(ApplicationStopped) {
        controller.shutdown()
    }
}

// ============================================================================
// REQUEST/RESPONSE DATA CLASSES
// ============================================================================

@Serializable
data class GitHubPRRequest(
    val owner: String,
    val repo: String,
    val filePath: String,
    val newContent: String,
    val fixDescription: String
)

@Serializable
data class LoadRepoRequest(val path: String)

@Serializable
data class CloneRepoRequest(
    val url: String,
    val localPath: String
)

@Serializable
data class SuppressRequest(val reason: String?)

@Serializable
data class DismissRequest(val reason: String?)

@Serializable
data class StartPreviewRequest(val platform: String)

@Serializable
data class FileChangedRequest(val path: String)

@Serializable
data class DiagnosticFilterParams(
    val severities: List<String>,
    val categories: List<String>,
    val moduleIds: List<String>,
    val sourceSets: List<String>,
    val search: String
)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String
)

@Serializable
data class WebSocketMessage(
    val type: String,
    val payload: String
)

@Serializable
data class WebSocketCommand(
    val command: String,
    val args: Map<String, String> = emptyMap()
)

@Serializable
data class AIAnalyzeRequest(
    val code: String = "",
    val filePath: String = "",
    val fileName: String = "unknown.kt",
    val context: String = "Kotlin project"
)
