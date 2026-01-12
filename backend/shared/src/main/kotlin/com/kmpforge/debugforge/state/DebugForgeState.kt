package com.kmpforge.debugforge.state

import kotlinx.serialization.Serializable
import com.kmpforge.debugforge.core.ModuleInfo
import com.kmpforge.debugforge.core.SharedCodeMetrics
import com.kmpforge.debugforge.diagnostics.Diagnostic
import com.kmpforge.debugforge.preview.PreviewState
import com.kmpforge.debugforge.ai.RefactorSuggestion

/**
 * Root state object exposed to the Compose Multiplatform frontend.
 * This is the single source of truth for all UI state.
 * 
 * The frontend subscribes to StateFlow<DebugForgeState> and recomposes
 * whenever this state changes. All mutations happen through DebugForgeController.
 */
@Serializable
data class DebugForgeState(
    /** Current repository loading/analysis status */
    val repoStatus: RepoStatus = RepoStatus.Idle,
    
    /** All detected KMP modules in the repository */
    val modules: List<ModuleInfo> = emptyList(),
    
    /** Active diagnostics from all analyzers */
    val diagnostics: List<Diagnostic> = emptyList(),
    
    /** Aggregated metrics about shared code coverage */
    val sharedCodeMetrics: SharedCodeMetrics = SharedCodeMetrics.EMPTY,
    
    /** Current preview session state across all platforms */
    val previews: PreviewState = PreviewState.Inactive,
    
    /** AI-generated refactor suggestions with diffs */
    val refactorSuggestions: List<RefactorSuggestion> = emptyList(),
    
    /** Timestamp of last state update for debugging */
    val lastUpdated: Long = 0L,
    
    /** Current analysis progress (0.0 to 1.0) */
    val analysisProgress: Float = 0f,
    
    /** Any fatal error that halted processing */
    val fatalError: FatalError? = null
) {
    companion object {
        val INITIAL = DebugForgeState()
    }
}

/**
 * Represents the current status of repository operations.
 */
@Serializable
sealed class RepoStatus {
    /** No repository loaded */
    @Serializable
    data object Idle : RepoStatus()
    
    /** Cloning a remote repository */
    @Serializable
    data class Cloning(
        val url: String,
        val progress: Float,
        val currentOperation: String
    ) : RepoStatus()
    
    /** Loading a local repository folder */
    @Serializable
    data class Loading(
        val path: String,
        val filesScanned: Int
    ) : RepoStatus()
    
    /** Indexing source files into SQLDelight database */
    @Serializable
    data class Indexing(
        val totalFiles: Int,
        val indexedFiles: Int,
        val currentFile: String
    ) : RepoStatus()
    
    /** Running static analysis passes */
    @Serializable
    data class Analyzing(
        val currentAnalyzer: String,
        val progress: Float
    ) : RepoStatus()
    
    /** Repository fully loaded and analyzed */
    @Serializable
    data class Ready(
        val repoPath: String,
        val repoName: String,
        val totalFiles: Int,
        val totalModules: Int,
        val loadedAt: Long
    ) : RepoStatus()
    
    /** Loading or analysis failed */
    @Serializable
    data class Failed(
        val error: String,
        val recoverable: Boolean
    ) : RepoStatus()
}

/**
 * Represents an unrecoverable error that halted the system.
 */
@Serializable
data class FatalError(
    val code: String,
    val message: String,
    val stackTrace: String? = null,
    val timestamp: Long
)
