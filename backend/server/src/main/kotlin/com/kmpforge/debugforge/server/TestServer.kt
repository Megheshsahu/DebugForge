package com.kmpforge.debugforge.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Local API models to avoid shared module dependencies
@Serializable
data class LoadRepoRequest(val path: String)

@Serializable
data class CloneRepoRequest(val url: String, val localPath: String)

@Serializable
data class LoadRepoResponse(
    val success: Boolean,
    val message: String,
    val repoId: String? = null,
    val repo: RepoInfo? = null,
    val error: String? = null
)

@Serializable
data class CloneRepoResponse(
    val success: Boolean,
    val message: String,
    val repoId: String? = null,
    val repo: RepoInfo? = null,
    val error: String? = null
)

@Serializable
data class RepoInfo(
    val id: String,
    val path: String,
    val name: String,
    val gitInfo: GitInfo? = null,
    val modules: List<ModuleInfo> = emptyList(),
    val buildSystem: String? = null,
    val lastAnalyzed: Long? = null
)

@Serializable
data class GitInfo(
    val branch: String,
    val commit: String,
    val remoteUrl: String,
    val isDirty: Boolean
)

@Serializable
data class ModuleInfo(
    val id: String,
    val name: String,
    val path: String,
    val type: String,
    val targets: List<String>,
    val dependencies: List<String>
)

/**
 * Simple test server for debugging repository loading
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8081
    val host = System.getenv("HOST") ?: "127.0.0.1"

    embeddedServer(Netty, port = port, host = host) {
        configureTestServer()
    }.start(wait = true)
}

fun Application.configureTestServer() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
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
        allowCredentials = true
        anyHost()
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, mapOf(
                "error" to "Internal server error",
                "message" to cause.message
            ))
        }
    }

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok", "message" to "Test server is running"))
        }

        // Load repository from local path
        post("/repo/load") {
            try {
                val request = call.receive<LoadRepoRequest>()

                // Mock successful response
                call.respond(LoadRepoResponse(
                    success = true,
                    message = "Repository loaded successfully (test server)",
                    repoId = request.path.hashCode().toString(),
                    repo = RepoInfo(
                        id = request.path.hashCode().toString(),
                        path = request.path,
                        name = "test-repo",
                        gitInfo = GitInfo("main", "abc123", "https://github.com/test/repo", false),
                        modules = listOf(
                            ModuleInfo("app", "app", ":app", "gradle", emptyList(), emptyList()),
                            ModuleInfo("core", "core", ":core", "gradle", emptyList(), emptyList())
                        ),
                        buildSystem = "gradle",
                        lastAnalyzed = System.currentTimeMillis()
                    )
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, LoadRepoResponse(
                    success = false,
                    message = "Error during repository loading",
                    error = e.message ?: "Unknown error"
                ))
            }
        }

        // Clone repository from URL
        post("/repo/clone") {
            try {
                val request = call.receive<CloneRepoRequest>()

                // Mock successful response
                val targetPath = request.localPath
                call.respond(CloneRepoResponse(
                    success = true,
                    message = "Repository cloned successfully (test server)",
                    repoId = targetPath.hashCode().toString(),
                    repo = RepoInfo(
                        id = targetPath.hashCode().toString(),
                        path = targetPath,
                        name = "cloned-test-repo",
                        gitInfo = GitInfo("main", "def456", request.url, false),
                        modules = listOf(
                            ModuleInfo("app", "app", ":app", "gradle", emptyList(), emptyList()),
                            ModuleInfo("core", "core", ":core", "gradle", emptyList(), emptyList())
                        ),
                        buildSystem = "gradle",
                        lastAnalyzed = System.currentTimeMillis()
                    )
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, CloneRepoResponse(
                    success = false,
                    message = "Error during repository cloning",
                    error = e.message ?: "Unknown error"
                ))
            }
        }
    }
}