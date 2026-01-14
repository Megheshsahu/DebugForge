package com.kmpforge.debugforge.preview

import com.kmpforge.debugforge.analysis.FileSystemReader
import com.kmpforge.debugforge.core.ModuleInfo
import com.kmpforge.debugforge.core.SourceSetPlatform
import com.kmpforge.debugforge.persistence.RepoIndexDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Orchestrates multi-platform preview sessions for KMP projects.
 * 
 * This is the central coordinator for:
 * 1. Running @Composable previews across platforms
 * 2. Managing preview sessions lifecycle
 * 3. Hot-reload and re-render triggers
 * 4. Screenshot/output capture for comparison
 */
class PreviewOrchestrator(
    private val dao: RepoIndexDao,
    private val fileSystem: FileSystemReader,
    private val previewRunnerFactory: PreviewRunnerFactory
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _sessions = MutableStateFlow<Map<String, PreviewSession>>(emptyMap())
    val sessions: StateFlow<Map<String, PreviewSession>> = _sessions.asStateFlow()
    
    private val _orchestratorState = MutableStateFlow(OrchestratorState())
    val orchestratorState: StateFlow<OrchestratorState> = _orchestratorState.asStateFlow()
    
    /**
     * Discovers all @Preview annotated functions in common code.
     */
    suspend fun discoverPreviews(repoPath: String): List<PreviewableComposable> {
        val previews = mutableListOf<PreviewableComposable>()
        
        // Get all common source files
        val commonFiles = dao.getFilesInSourceSets(repoPath, listOf("commonMain"))
        
        for (file in commonFiles) {
            val content = fileSystem.readFile(file.path)
            
            // Find @Preview annotated functions
            val previewMatches = PREVIEW_PATTERN.findAll(content)
            
            for (match in previewMatches) {
                val previewAnnotation = match.groupValues[1]
                val functionName = match.groupValues[2]
                val lineNumber = content.substring(0, match.range.first).count { it == '\n' } + 1
                
                // Parse @Preview parameters
                val params = parsePreviewParams(previewAnnotation)
                
                previews.add(PreviewableComposable(
                    id = "${file.path}:$functionName",
                    functionName = functionName,
                    filePath = file.path,
                    relativePath = file.relativePath,
                    lineNumber = lineNumber,
                    packageName = file.packageName ?: "",
                    previewParams = params,
                    supportedPlatforms = detectSupportedPlatforms(content, functionName)
                ))
            }
        }
        
        _orchestratorState.update { it.copy(
            discoveredPreviews = previews,
            lastDiscoveryAt = Clock.System.now().toEpochMilliseconds()
        ) }
        
        return previews
    }
    
    /**
     * Starts a preview session for a specific composable on a platform.
     */
    suspend fun startPreview(
        preview: PreviewableComposable,
        platform: SourceSetPlatform
    ): PreviewSession {
        val sessionId = "${preview.id}:${platform.name}"
        
        // Check if session already exists
        _sessions.value[sessionId]?.let { existing ->
            if (existing.state == PreviewSessionState.RUNNING) {
                return existing
            }
        }
        
        // Get the appropriate runner for the platform
        val runner = previewRunnerFactory.createRunner(platform)
        
        val session = PreviewSession(
            id = sessionId,
            preview = preview,
            platform = platform,
            runner = runner,
            state = PreviewSessionState.INITIALIZING,
            startedAt = Clock.System.now().toEpochMilliseconds()
        )
        
        _sessions.update { it + (sessionId to session) }
        
        // Start the preview in background
        scope.launch {
            try {
                val updatedSession = session.copy(state = PreviewSessionState.BUILDING)
                _sessions.update { it + (sessionId to updatedSession) }
                
                // Build preview module
                runner.buildPreviewModule(preview)
                
                val runningSession = updatedSession.copy(state = PreviewSessionState.RUNNING)
                _sessions.update { it + (sessionId to runningSession) }
                
                // Start rendering
                runner.startRendering(preview)
                
                // Collect render outputs
                runner.renderOutputs.collect { output ->
                    val withOutput = _sessions.value[sessionId]?.copy(
                        lastOutput = output,
                        lastUpdatedAt = Clock.System.now().toEpochMilliseconds()
                    )
                    withOutput?.let { s ->
                        _sessions.update { it + (sessionId to s) }
                    }
                }
            } catch (e: Exception) {
                val failedSession = session.copy(
                    state = PreviewSessionState.FAILED,
                    error = e.message ?: "Unknown error"
                )
                _sessions.update { it + (sessionId to failedSession) }
            }
        }
        
        return session
    }
    
    /**
     * Starts previews for a composable across all supported platforms.
     */
    suspend fun startMultiPlatformPreview(
        preview: PreviewableComposable
    ): List<PreviewSession> {
        return preview.supportedPlatforms.map { platform ->
            startPreview(preview, platform)
        }
    }
    
    /**
     * Stops a preview session.
     */
    suspend fun stopPreview(sessionId: String) {
        val session = _sessions.value[sessionId] ?: return
        
        session.runner.stopRendering()
        
        val stoppedSession = session.copy(state = PreviewSessionState.STOPPED)
        _sessions.update { it + (sessionId to stoppedSession) }
    }
    
    /**
     * Stops all preview sessions.
     */
    suspend fun stopAllPreviews() {
        _sessions.value.keys.forEach { sessionId ->
            stopPreview(sessionId)
        }
    }
    
    /**
     * Triggers a hot-reload for file changes.
     */
    suspend fun notifyFileChanged(filePath: String) {
        // Find sessions affected by this file
        val affectedSessions = _sessions.value.values.filter { session ->
            session.preview.filePath == filePath ||
            session.preview.dependencies.contains(filePath)
        }
        
        for (session in affectedSessions) {
            if (session.state == PreviewSessionState.RUNNING) {
                scope.launch {
                    try {
                        val reloadingSession = session.copy(state = PreviewSessionState.RELOADING)
                        _sessions.update { it + (session.id to reloadingSession) }
                        
                        session.runner.hotReload(session.preview)
                        
                        val runningSession = reloadingSession.copy(
                            state = PreviewSessionState.RUNNING,
                            lastUpdatedAt = Clock.System.now().toEpochMilliseconds()
                        )
                        _sessions.update { it + (session.id to runningSession) }
                    } catch (e: Exception) {
                        val failedSession = session.copy(
                            state = PreviewSessionState.FAILED,
                            error = "Hot reload failed: ${e.message}"
                        )
                        _sessions.update { it + (session.id to failedSession) }
                    }
                }
            }
        }
    }
    
    /**
     * Captures a screenshot/output from a preview session.
     */
    suspend fun captureOutput(sessionId: String): PreviewOutput? {
        val session = _sessions.value[sessionId] ?: return null
        
        return if (session.state == PreviewSessionState.RUNNING) {
            session.runner.captureOutput()
        } else {
            session.lastOutput
        }
    }
    
    /**
     * Compares outputs from the same preview across platforms.
     */
    suspend fun compareAcrossPlatforms(previewId: String): PlatformComparisonResult {
        val relevantSessions = _sessions.value.values.filter { 
            it.preview.id == previewId && it.lastOutput != null 
        }
        
        if (relevantSessions.size < 2) {
            return PlatformComparisonResult(
                previewId = previewId,
                platforms = relevantSessions.map { it.platform },
                differences = emptyList(),
                overallSimilarity = 1f
            )
        }
        
        val differences = mutableListOf<PlatformDifference>()
        
        // Compare each pair of platforms
        for (i in relevantSessions.indices) {
            for (j in i + 1 until relevantSessions.size) {
                val session1 = relevantSessions[i]
                val session2 = relevantSessions[j]
                
                val diff = comparePlatformOutputs(session1, session2)
                if (diff != null) {
                    differences.add(diff)
                }
            }
        }
        
        val avgSimilarity = if (differences.isNotEmpty()) {
            differences.map { it.similarity }.average().toFloat()
        } else {
            1f
        }
        
        return PlatformComparisonResult(
            previewId = previewId,
            platforms = relevantSessions.map { it.platform },
            differences = differences,
            overallSimilarity = avgSimilarity
        )
    }
    
    /**
     * Cleans up and releases resources.
     */
    fun shutdown() {
        scope.cancel()
        _sessions.value.values.forEach { session ->
            session.runner.dispose()
        }
        _sessions.value = emptyMap()
    }
    
    private fun parsePreviewParams(annotation: String): PreviewParams {
        val params = mutableMapOf<String, String>()
        
        PARAM_PATTERN.findAll(annotation).forEach { match ->
            params[match.groupValues[1]] = match.groupValues[2]
        }
        
        return PreviewParams(
            name = params["name"]?.removeSurrounding("\"") ?: "",
            group = params["group"]?.removeSurrounding("\"") ?: "",
            widthDp = params["widthDp"]?.toIntOrNull() ?: 0,
            heightDp = params["heightDp"]?.toIntOrNull() ?: 0,
            showBackground = params["showBackground"]?.toBooleanStrictOrNull() ?: false,
            backgroundColor = params["backgroundColor"]?.toLongOrNull() ?: 0L
        )
    }
    
    private fun detectSupportedPlatforms(
        content: String, 
        functionName: String
    ): List<SourceSetPlatform> {
        // Check for platform-specific APIs used in the function
        val platforms = mutableListOf<SourceSetPlatform>()
        
        // Common code - supports all compose platforms by default
        platforms.add(SourceSetPlatform.COMMON)
        
        // Check if uses Android-specific APIs
        if (!content.contains("android.", ignoreCase = false) &&
            !content.contains("AndroidView", ignoreCase = false)) {
            platforms.add(SourceSetPlatform.JVM) // Desktop
            platforms.add(SourceSetPlatform.JS) // Web
            platforms.add(SourceSetPlatform.WASM)
            platforms.add(SourceSetPlatform.IOS)
        }
        
        return platforms
    }
    
    private fun comparePlatformOutputs(
        session1: PreviewSession,
        session2: PreviewSession
    ): PlatformDifference? {
        val output1 = session1.lastOutput ?: return null
        val output2 = session2.lastOutput ?: return null
        
        // Simple comparison - in real implementation would use image diff algorithms
        val similarity = when {
            output1.renderType != output2.renderType -> 0.5f
            output1.width != output2.width || output1.height != output2.height -> 0.7f
            output1.contentHash == output2.contentHash -> 1.0f
            else -> 0.85f // Visual differences require actual comparison
        }
        
        return PlatformDifference(
            platform1 = session1.platform,
            platform2 = session2.platform,
            similarity = similarity,
            issues = buildList {
                if (output1.renderType != output2.renderType) {
                    add("Different render types: ${output1.renderType} vs ${output2.renderType}")
                }
                if (output1.width != output2.width) {
                    add("Width differs: ${output1.width} vs ${output2.width}")
                }
                if (output1.height != output2.height) {
                    add("Height differs: ${output1.height} vs ${output2.height}")
                }
            }
        )
    }
    
    companion object {
        private val PREVIEW_PATTERN = Regex(
            """(@Preview(?:\([^)]*\))?)\s*@Composable\s+(?:private\s+|internal\s+)?fun\s+(\w+)\s*\(""",
            RegexOption.MULTILINE
        )
        
        private val PARAM_PATTERN = Regex("""(\w+)\s*=\s*([^,)]+)""")
    }
}

/**
 * State of the preview orchestrator.
 */
@Serializable
data class OrchestratorState(
    val discoveredPreviews: List<PreviewableComposable> = emptyList(),
    val lastDiscoveryAt: Long = 0,
    val activeSessions: Int = 0,
    val errors: List<String> = emptyList()
)

/**
 * A composable function that can be previewed.
 */
@Serializable
data class PreviewableComposable(
    val id: String,
    val functionName: String,
    val filePath: String,
    val relativePath: String,
    val lineNumber: Int,
    val packageName: String,
    val previewParams: PreviewParams,
    val supportedPlatforms: List<SourceSetPlatform>,
    val dependencies: List<String> = emptyList()
)

/**
 * Parameters from @Preview annotation.
 */
@Serializable
data class PreviewParams(
    val name: String = "",
    val group: String = "",
    val widthDp: Int = 0,
    val heightDp: Int = 0,
    val showBackground: Boolean = false,
    val backgroundColor: Long = 0L
)

/**
 * State of a preview session.
 */
enum class PreviewSessionState {
    INITIALIZING,
    BUILDING,
    RUNNING,
    RELOADING,
    STOPPED,
    FAILED
}

/**
 * An active preview session.
 */
data class PreviewSession(
    val id: String,
    val preview: PreviewableComposable,
    val platform: SourceSetPlatform,
    val runner: PreviewRunner,
    val state: PreviewSessionState,
    val startedAt: Long,
    val lastUpdatedAt: Long = startedAt,
    val lastOutput: PreviewOutput? = null,
    val error: String? = null
)

/**
 * Output from a preview render.
 */
@Serializable
data class PreviewOutput(
    val renderType: RenderType,
    val width: Int,
    val height: Int,
    val contentHash: String,
    val imageData: ByteArray? = null,
    val htmlContent: String? = null,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreviewOutput) return false
        return contentHash == other.contentHash
    }
    
    override fun hashCode(): Int = contentHash.hashCode()
}

/**
 * Type of render output.
 */
@Serializable
enum class RenderType {
    PNG_IMAGE,
    SVG_IMAGE,
    HTML_CANVAS,
    SKIA_PICTURE
}

/**
 * Result of comparing previews across platforms.
 */
@Serializable
data class PlatformComparisonResult(
    val previewId: String,
    val platforms: List<SourceSetPlatform>,
    val differences: List<PlatformDifference>,
    val overallSimilarity: Float
)

/**
 * Difference between two platform outputs.
 */
@Serializable
data class PlatformDifference(
    val platform1: SourceSetPlatform,
    val platform2: SourceSetPlatform,
    val similarity: Float,
    val issues: List<String>
)

/**
 * Factory for creating platform-specific preview runners.
 */
interface PreviewRunnerFactory {
    fun createRunner(platform: SourceSetPlatform): PreviewRunner
}

/**
 * Runs previews on a specific platform.
 */
interface PreviewRunner {
    /**
     * Flow of render outputs as they become available.
     */
    val renderOutputs: Flow<PreviewOutput>
    
    /**
     * Builds the preview module for the given composable.
     */
    suspend fun buildPreviewModule(preview: PreviewableComposable)
    
    /**
     * Starts rendering the preview.
     */
    suspend fun startRendering(preview: PreviewableComposable)
    
    /**
     * Stops rendering.
     */
    suspend fun stopRendering()
    
    /**
     * Hot-reloads the preview after file changes.
     */
    suspend fun hotReload(preview: PreviewableComposable)
    
    /**
     * Captures the current output.
     */
    suspend fun captureOutput(): PreviewOutput
    
    /**
     * Releases resources.
     */
    fun dispose()
}
