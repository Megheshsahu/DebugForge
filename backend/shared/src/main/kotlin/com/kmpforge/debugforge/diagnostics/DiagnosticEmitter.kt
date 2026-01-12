package com.kmpforge.debugforge.diagnostics

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.Clock

/**
 * Emits diagnostics for real-time consumption by the frontend.
 * Supports streaming diagnostics as they are discovered, rather than
 * waiting for all analysis to complete.
 */
class DiagnosticEmitter {
    
    private val _diagnosticStream = MutableSharedFlow<DiagnosticEvent>(
        replay = 0,
        extraBufferCapacity = 1000
    )
    val diagnosticStream: SharedFlow<DiagnosticEvent> = _diagnosticStream.asSharedFlow()
    
    private val _activeFilters = MutableSharedFlow<DiagnosticFilter>(replay = 1)
    val activeFilters: SharedFlow<DiagnosticFilter> = _activeFilters.asSharedFlow()
    
    /**
     * Emits a new diagnostic.
     */
    suspend fun emit(diagnostic: Diagnostic) {
        _diagnosticStream.emit(DiagnosticEvent.Added(diagnostic))
    }
    
    /**
     * Emits multiple diagnostics at once.
     */
    suspend fun emitBatch(diagnostics: List<Diagnostic>) {
        diagnostics.forEach { diagnostic ->
            _diagnosticStream.emit(DiagnosticEvent.Added(diagnostic))
        }
    }
    
    /**
     * Emits that a diagnostic was resolved/fixed.
     */
    suspend fun emitResolved(diagnosticId: String, resolution: DiagnosticResolution) {
        _diagnosticStream.emit(DiagnosticEvent.Resolved(diagnosticId, resolution))
    }
    
    /**
     * Emits that a diagnostic was dismissed by the user.
     */
    suspend fun emitDismissed(diagnosticId: String) {
        _diagnosticStream.emit(DiagnosticEvent.Dismissed(diagnosticId))
    }
    
    /**
     * Emits that all diagnostics for a file should be cleared (file deleted/changed).
     */
    suspend fun emitFileClear(filePath: String) {
        _diagnosticStream.emit(DiagnosticEvent.FileClear(filePath))
    }
    
    /**
     * Emits analysis progress update.
     */
    suspend fun emitProgress(progress: AnalysisProgress) {
        _diagnosticStream.emit(DiagnosticEvent.Progress(progress))
    }
    
    /**
     * Updates the active diagnostic filter.
     */
    suspend fun setFilter(filter: DiagnosticFilter) {
        _activeFilters.emit(filter)
    }
    
    /**
     * Creates a filtered view of diagnostics based on current filters.
     */
    fun filterDiagnostics(
        diagnostics: List<Diagnostic>,
        filter: DiagnosticFilter
    ): List<Diagnostic> {
        return diagnostics.filter { diagnostic ->
            // Filter by severity
            if (filter.severities.isNotEmpty() && diagnostic.severity !in filter.severities) {
                return@filter false
            }
            
            // Filter by category
            if (filter.categories.isNotEmpty() && diagnostic.category !in filter.categories) {
                return@filter false
            }
            
            // Filter by source
            if (filter.sources.isNotEmpty() && diagnostic.source !in filter.sources) {
                return@filter false
            }
            
            // Filter by file path pattern
            if (filter.filePattern != null) {
                val regex = filter.filePattern.toRegex()
                if (!regex.containsMatchIn(diagnostic.location.filePath)) {
                    return@filter false
                }
            }
            
            // Filter by module
            if (filter.modules.isNotEmpty() && diagnostic.location.moduleId !in filter.modules) {
                return@filter false
            }
            
            // Filter by tags
            if (filter.requiredTags.isNotEmpty()) {
                if (!diagnostic.tags.containsAll(filter.requiredTags)) {
                    return@filter false
                }
            }
            
            // Exclude dismissed
            if (filter.excludeDismissed && !diagnostic.isActive) {
                return@filter false
            }
            
            true
        }
    }
    
    /**
     * Groups diagnostics by file for tree view display.
     */
    fun groupByFile(diagnostics: List<Diagnostic>): Map<String, List<Diagnostic>> {
        return diagnostics.groupBy { it.location.filePath }
    }
    
    /**
     * Groups diagnostics by category for category view display.
     */
    fun groupByCategory(diagnostics: List<Diagnostic>): Map<DiagnosticCategory, List<Diagnostic>> {
        return diagnostics.groupBy { it.category }
    }
    
    /**
     * Groups diagnostics by module for module view display.
     */
    fun groupByModule(diagnostics: List<Diagnostic>): Map<String, List<Diagnostic>> {
        return diagnostics.groupBy { it.location.moduleId }
    }
}

/**
 * Events emitted by the diagnostic stream.
 */
@kotlinx.serialization.Serializable
sealed class DiagnosticEvent {
    /** A new diagnostic was added */
    @kotlinx.serialization.Serializable
    data class Added(val diagnostic: Diagnostic) : DiagnosticEvent()
    
    /** A diagnostic was resolved (fixed) */
    @kotlinx.serialization.Serializable
    data class Resolved(
        val diagnosticId: String,
        val resolution: DiagnosticResolution
    ) : DiagnosticEvent()
    
    /** A diagnostic was dismissed by the user */
    @kotlinx.serialization.Serializable
    data class Dismissed(val diagnosticId: String) : DiagnosticEvent()
    
    /** All diagnostics for a file were cleared */
    @kotlinx.serialization.Serializable
    data class FileClear(val filePath: String) : DiagnosticEvent()
    
    /** Analysis progress update */
    @kotlinx.serialization.Serializable
    data class Progress(val progress: AnalysisProgress) : DiagnosticEvent()
}

/**
 * How a diagnostic was resolved.
 */
@kotlinx.serialization.Serializable
data class DiagnosticResolution(
    val method: ResolutionMethod,
    val fixApplied: DiagnosticFix?,
    val timestamp: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
)

@kotlinx.serialization.Serializable
enum class ResolutionMethod {
    AUTO_FIX,       // Applied automatic fix
    MANUAL_EDIT,    // User manually edited the code
    CODE_DELETED,   // The problematic code was deleted
    FALSE_POSITIVE  // User marked as false positive
}

/**
 * Analysis progress for streaming updates.
 */
@kotlinx.serialization.Serializable
data class AnalysisProgress(
    val phase: String,
    val currentFile: String?,
    val filesProcessed: Int,
    val totalFiles: Int,
    val diagnosticsFound: Int
) {
    val percentage: Float get() = if (totalFiles > 0) filesProcessed.toFloat() / totalFiles else 0f
}

/**
 * Filter criteria for diagnostics.
 */
data class DiagnosticFilter(
    val severities: Set<DiagnosticSeverity> = emptySet(),
    val categories: Set<DiagnosticCategory> = emptySet(),
    val sources: Set<AnalyzerSource> = emptySet(),
    val filePattern: String? = null,
    val modules: Set<String> = emptySet(),
    val requiredTags: Set<DiagnosticTag> = emptySet(),
    val excludeDismissed: Boolean = true
) {
    companion object {
        val ALL = DiagnosticFilter()
        
        val ERRORS_ONLY = DiagnosticFilter(
            severities = setOf(DiagnosticSeverity.ERROR)
        )
        
        val ERRORS_AND_WARNINGS = DiagnosticFilter(
            severities = setOf(DiagnosticSeverity.ERROR, DiagnosticSeverity.WARNING)
        )
        
        val FIXABLE_ONLY = DiagnosticFilter(
            requiredTags = setOf(DiagnosticTag.FIXABLE)
        )
    }
}
