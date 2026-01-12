package com.kmpforge.debugforge.app

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class LoadRepoRequest(val path: String)

@Serializable
data class CloneRepoRequest(val url: String, val localPath: String)

@Serializable
data class LoadRepoResponse(val repoId: String, val message: String)

@Serializable
data class DiagnosticLocation(
    val filePath: String,
    val relativeFilePath: String? = null,
    val moduleId: String? = null,
    val sourceSet: String? = null,
    val startLine: Int = 0,
    val startColumn: Int = 0,
    val endLine: Int = 0,
    val endColumn: Int = 0
)

@Serializable
data class DiagnosticResponse(
    val id: String,
    val severity: String,
    val category: String,
    val message: String,
    val explanation: String? = null,
    val location: DiagnosticLocation? = null,
    val source: String? = null,
    val tags: List<String>? = null,
    val codeSnippet: String? = null,
    val fixes: List<DiagnosticFixResponse>? = null
)

@Serializable
data class DiagnosticFixResponse(
    val title: String? = null,
    val description: String? = null,
    val newText: String? = null,
    val isPreferred: Boolean = false,
    val confidence: Float = 0.0f
)

@Serializable
data class SourceSetResponse(
    val name: String,
    val platform: String? = null,
    val sourcePath: String? = null,
    val kotlinFileCount: Int = 0,
    val kotlinLinesOfCode: Int = 0
)

@Serializable
data class ModuleResponse(
    val id: String? = null,
    val name: String,
    val path: String,
    val gradlePath: String? = null,
    val sourceSets: List<SourceSetResponse> = emptyList(),
    val hasCommonCode: Boolean = false
)

@Serializable
data class SuggestionResponse(
    val id: String,
    val title: String,
    val rationale: String,
    val confidence: Double = 0.0,
    val category: String? = null,
    val priority: String? = null,
    val unifiedDiff: String? = null,
    val source: String? = null,
    val changes: List<FileChangeResponse>? = null
)

@Serializable
data class FileChangeResponse(
    val filePath: String? = null,
    val changeType: String? = null,
    val hunks: List<DiffHunkResponse>? = null
)

@Serializable
data class DiffHunkResponse(
    val originalStart: Int = 0,
    val originalCount: Int = 0,
    val modifiedStart: Int = 0,
    val modifiedCount: Int = 0,
    val lines: List<DiffLineResponse>? = null
)

@Serializable
data class DiffLineResponse(
    val type: String? = null,
    val content: String = "",
    val originalLineNumber: Int? = null,
    val modifiedLineNumber: Int? = null
)

@Serializable
data class MetricsResponse(
    val totalLinesOfCode: Int = 0,
    val sharedLinesOfCode: Int = 0,
    val sharedCodePercentage: Double = 0.0,
    val expectDeclarations: Int = 0,
    val actualImplementations: Int = 0
)

// AI Analysis request/response
@Serializable
data class AIAnalyzeRequest(
    val code: String,
    val fileName: String = "",
    val filePath: String = "",
    val context: String = ""
)

@Serializable
data class AIAnalyzeResponse(
    val status: String,
    val analysis: String = "{}",
    val model: String = ""
)

@Serializable
data class AISuggestion(
    val title: String,
    val rationale: String,
    val beforeCode: String = "",
    val afterCode: String = "",
    val confidence: Double = 0.0
)

@Serializable
data class AIAnalysisResult(
    val suggestions: List<AISuggestion> = emptyList(),
    val summary: String = ""
)

class DebugForgeApiClient {
    private val baseUrl = "http://localhost:18999"
    
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    suspend fun checkHealth(): Boolean {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/health")
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun loadRepo(path: String): LoadResult {
        return try {
            // Start the load operation
            val response: HttpResponse = httpClient.post("$baseUrl/repo/load") {
                contentType(ContentType.Application.Json)
                setBody(LoadRepoRequest(path))
            }
            
            if (!response.status.isSuccess()) {
                return LoadResult.Failed("Server returned ${response.status}")
            }
            
            // Poll for completion (load is async on server)
            var attempts = 0
            val maxAttempts = 60 // 1 minute timeout
            
            while (attempts < maxAttempts) {
                delay(1000)
                attempts++
                
                val stateResponse: HttpResponse = httpClient.get("$baseUrl/state")
                if (!stateResponse.status.isSuccess()) continue
                
                val stateJson: JsonObject = stateResponse.body()
                val repoStatus = stateJson["repoStatus"]?.jsonObject ?: continue
                val statusType = repoStatus["type"]?.jsonPrimitive?.content ?: continue
                
                when {
                    statusType.contains("Ready") -> {
                        return LoadResult.Success
                    }
                    statusType.contains("Failed") -> {
                        val error = repoStatus["error"]?.jsonPrimitive?.content ?: "Load failed"
                        return LoadResult.Failed(error)
                    }
                    statusType.contains("Loading") || statusType.contains("Indexing") || statusType.contains("Analyzing") -> {
                        continue
                    }
                }
            }
            
            LoadResult.Failed("Load timed out after 1 minute")
        } catch (e: Exception) {
            println("Error loading repo: ${e.message}")
            LoadResult.Failed(e.message ?: "Unknown error")
        }
    }
    
    sealed class LoadResult {
        data object Success : LoadResult()
        data class Failed(val error: String) : LoadResult()
    }
    
    suspend fun cloneRepo(url: String, localPath: String): CloneResult {
        return try {
            // Start the clone operation
            val response: HttpResponse = httpClient.post("$baseUrl/repo/clone") {
                contentType(ContentType.Application.Json)
                setBody(CloneRepoRequest(url, localPath))
            }
            
            if (!response.status.isSuccess()) {
                return CloneResult.Failed("Server returned ${response.status}")
            }
            
            // Poll for completion (clone is async on server)
            var attempts = 0
            val maxAttempts = 120 // 2 minutes timeout (120 * 1 second)
            
            while (attempts < maxAttempts) {
                delay(1000) // Wait 1 second between polls
                attempts++
                
                val stateResponse: HttpResponse = httpClient.get("$baseUrl/state")
                if (!stateResponse.status.isSuccess()) continue
                
                val stateJson: JsonObject = stateResponse.body()
                val repoStatus = stateJson["repoStatus"]?.jsonObject ?: continue
                val statusType = repoStatus["type"]?.jsonPrimitive?.content ?: continue
                
                when {
                    statusType.contains("Ready") -> {
                        return CloneResult.Success
                    }
                    statusType.contains("Failed") -> {
                        val error = repoStatus["error"]?.jsonPrimitive?.content ?: "Clone failed"
                        return CloneResult.Failed(error)
                    }
                    statusType.contains("Cloning") || statusType.contains("Indexing") || statusType.contains("Analyzing") -> {
                        // Still in progress, continue polling
                        continue
                    }
                }
            }
            
            CloneResult.Failed("Clone timed out after 2 minutes")
        } catch (e: Exception) {
            println("Error cloning repo: ${e.message}")
            CloneResult.Failed(e.message ?: "Unknown error")
        }
    }
    
    sealed class CloneResult {
        data object Success : CloneResult()
        data class Failed(val error: String) : CloneResult()
    }
    
    fun isGitHubUrl(input: String): Boolean {
        return input.startsWith("https://github.com/") || 
               input.startsWith("git@github.com:") ||
               input.startsWith("http://github.com/")
    }
    
    suspend fun runAnalysis(repoId: String): Boolean {
        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/repo/$repoId/analyze")
            response.status.isSuccess()
        } catch (e: Exception) {
            println("Error running analysis: ${e.message}")
            false
        }
    }
    
    suspend fun getDiagnostics(repoId: String): List<DiagnosticDisplay> {
        return try {
            val response: List<DiagnosticResponse> = httpClient.get("$baseUrl/repo/$repoId/diagnostics").body()
            response.map { 
                DiagnosticDisplay(
                    id = it.id,
                    severity = it.severity,
                    category = it.category,
                    message = it.message,
                    filePath = it.location?.filePath ?: "",
                    line = it.location?.startLine ?: 0,
                    codeSnippet = it.codeSnippet ?: it.explanation
                )
            }
        } catch (e: Exception) {
            println("Error getting diagnostics: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun getModules(repoId: String): List<ModuleDisplay> {
        return try {
            val response: List<ModuleResponse> = httpClient.get("$baseUrl/repo/$repoId/modules").body()
            response.map {
                ModuleDisplay(
                    name = it.name,
                    path = it.path,
                    fileCount = it.sourceSets.sumOf { ss -> ss.kotlinFileCount },
                    sourceSets = it.sourceSets.map { ss -> ss.name }
                )
            }
        } catch (e: Exception) {
            println("Error getting modules: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun getSuggestions(repoId: String): List<SuggestionDisplay> {
        return try {
            val response: List<SuggestionResponse> = httpClient.get("$baseUrl/repo/$repoId/suggestions").body()
            response.map {
                SuggestionDisplay(
                    id = it.id,
                    title = it.title,
                    rationale = it.rationale,
                    beforeCode = it.unifiedDiff,
                    afterCode = null
                )
            }
        } catch (e: Exception) {
            println("Error getting suggestions: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun getMetrics(repoId: String): MetricsDisplay? {
        return try {
            val response: MetricsResponse = httpClient.get("$baseUrl/repo/$repoId/metrics").body()
            MetricsDisplay(
                totalFiles = 0,
                totalLines = response.totalLinesOfCode,
                sharedCodePercent = response.sharedCodePercentage
            )
        } catch (e: Exception) {
            println("Error getting metrics: ${e.message}")
            null
        }
    }
    
    // Global state endpoints (used after clone completes)
    suspend fun getGlobalDiagnostics(): List<DiagnosticDisplay> {
        return try {
            val response: List<DiagnosticResponse> = httpClient.get("$baseUrl/diagnostics").body()
            response.map { 
                DiagnosticDisplay(
                    id = it.id,
                    severity = it.severity,
                    category = it.category,
                    message = it.message,
                    filePath = it.location?.filePath ?: "",
                    line = it.location?.startLine ?: 0,
                    codeSnippet = it.explanation
                )
            }
        } catch (e: Exception) {
            println("Error getting global diagnostics: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun getGlobalModules(): List<ModuleDisplay> {
        return try {
            val response: List<ModuleResponse> = httpClient.get("$baseUrl/modules").body()
            response.map {
                ModuleDisplay(
                    name = it.name,
                    path = it.path,
                    fileCount = it.sourceSets.sumOf { ss -> ss.kotlinFileCount },
                    sourceSets = it.sourceSets.map { ss -> ss.name }
                )
            }
        } catch (e: Exception) {
            println("Error getting global modules: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun getGlobalSuggestions(): List<SuggestionDisplay> {
        return try {
            val response: List<SuggestionResponse> = httpClient.get("$baseUrl/refactors").body()
            response.map { suggestion ->
                // Extract before/after code from unified diff if available
                val (beforeCode, afterCode) = extractBeforeAfterFromUnifiedDiff(suggestion.unifiedDiff)
                
                SuggestionDisplay(
                    id = suggestion.id,
                    title = suggestion.title,
                    rationale = suggestion.rationale,
                    beforeCode = beforeCode,
                    afterCode = afterCode
                )
            }
        } catch (e: Exception) {
            println("Error getting global suggestions: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Extract before and after code from unified diff
     */
    private fun extractBeforeAfterFromUnifiedDiff(unifiedDiff: String?): Pair<String?, String?> {
        if (unifiedDiff.isNullOrBlank()) return Pair(null, null)
        
        val lines = unifiedDiff.lines()
        val beforeLines = mutableListOf<String>()
        val afterLines = mutableListOf<String>()
        
        var inDiff = false
        
        for (line in lines) {
            when {
                line.startsWith("---") || line.startsWith("+++") -> continue
                line.startsWith("@@") -> inDiff = true
                inDiff && line.startsWith("-") -> beforeLines.add(line.substring(1))
                inDiff && line.startsWith("+") -> afterLines.add(line.substring(1))
                inDiff && !line.startsWith("-") && !line.startsWith("+") && line.isNotBlank() -> {
                    // Context line - add to both
                    beforeLines.add(line)
                    afterLines.add(line)
                }
            }
        }
        
        val before = if (beforeLines.isNotEmpty()) beforeLines.joinToString("\n") else null
        val after = if (afterLines.isNotEmpty()) afterLines.joinToString("\n") else null
        
        return Pair(before, after)
    }
    
    suspend fun getGlobalMetrics(): MetricsDisplay? {
        return try {
            val response: MetricsResponse = httpClient.get("$baseUrl/metrics").body()
            MetricsDisplay(
                totalFiles = 0, // Not in response, use modules for this
                totalLines = response.totalLinesOfCode,
                sharedCodePercent = response.sharedCodePercentage
            )
        } catch (e: Exception) {
            println("Error getting global metrics: ${e.message}")
            null
        }
    }
    
    /**
     * Analyze code using AI (xAI Grok)
     * Returns AI-powered suggestions for improvements
     */
    suspend fun analyzeWithAI(
        code: String, 
        fileName: String = "", 
        filePath: String = "",
        context: String = "KMP Project"
    ): AIAnalysisResult {
        return try {
            val httpResponse = httpClient.post("$baseUrl/ai/analyze") {
                contentType(ContentType.Application.Json)
                setBody(AIAnalyzeRequest(
                    code = code,
                    fileName = fileName,
                    filePath = filePath,
                    context = context
                ))
            }
            
            // First try to parse as JSON to handle any response format
            val responseText = httpResponse.bodyAsText()
            val jsonResponse = try {
                Json { ignoreUnknownKeys = true }.parseToJsonElement(responseText).jsonObject
            } catch (e: Exception) {
                return AIAnalysisResult(summary = "Invalid response from server")
            }
            
            val status = jsonResponse["status"]?.jsonPrimitive?.content ?: "error"
            val analysis = jsonResponse["analysis"]?.jsonPrimitive?.content ?: "{}"
            val error = jsonResponse["error"]?.jsonPrimitive?.content
            
            if (status == "success" && analysis.isNotEmpty() && analysis != "{}") {
                // Parse the AI response JSON
                try {
                    Json.decodeFromString<AIAnalysisResult>(analysis)
                } catch (e: Exception) {
                    println("Error parsing AI analysis: ${e.message}")
                    AIAnalysisResult(summary = "AI analysis returned but parsing failed: ${analysis.take(200)}")
                }
            } else if (error != null) {
                AIAnalysisResult(summary = error)
            } else {
                AIAnalysisResult(summary = "No AI suggestions generated")
            }
        } catch (e: Exception) {
            println("Error calling AI analysis: ${e.message}")
            AIAnalysisResult(summary = "AI analysis unavailable: ${e.message}")
        }
    }
    
    /**
     * Analyze a file path directly using AI
     */
    suspend fun analyzeFileWithAI(filePath: String, context: String = "KMP Project"): AIAnalysisResult {
        return try {
            val httpResponse = httpClient.post("$baseUrl/ai/analyze") {
                contentType(ContentType.Application.Json)
                setBody(AIAnalyzeRequest(
                    code = "",
                    fileName = filePath.substringAfterLast("/").substringAfterLast("\\"),
                    filePath = filePath,
                    context = context
                ))
            }
            
            // First try to parse as JSON to handle any response format
            val responseText = httpResponse.bodyAsText()
            val jsonResponse = try {
                Json { ignoreUnknownKeys = true }.parseToJsonElement(responseText).jsonObject
            } catch (e: Exception) {
                return AIAnalysisResult(summary = "Invalid response from server")
            }
            
            val status = jsonResponse["status"]?.jsonPrimitive?.content ?: "error"
            val analysis = jsonResponse["analysis"]?.jsonPrimitive?.content ?: "{}"
            val error = jsonResponse["error"]?.jsonPrimitive?.content
            
            if (status == "success" && analysis.isNotEmpty() && analysis != "{}") {
                try {
                    Json.decodeFromString<AIAnalysisResult>(analysis)
                } catch (e: Exception) {
                    println("Error parsing AI analysis: ${e.message}")
                    AIAnalysisResult(summary = "AI analysis returned but parsing failed")
                }
            } else if (error != null) {
                AIAnalysisResult(summary = error)
            } else {
                AIAnalysisResult(summary = "No AI suggestions generated")
            }
        } catch (e: Exception) {
            println("Error calling AI analysis: ${e.message}")
            AIAnalysisResult(summary = "AI analysis unavailable: ${e.message}")
        }
    }
}

data class MetricsDisplay(
    val totalFiles: Int,
    val totalLines: Int,
    val sharedCodePercent: Double
)
