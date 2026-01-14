package com.kmpforge.debugforge.diagnostics

import com.kmpforge.debugforge.analysis.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * Central engine that orchestrates all diagnostic analyzers.
 * Runs analyzers in parallel and aggregates results into a unified diagnostic list.
 */
class DiagnosticEngine(
    private val expectActualAnalyzer: ExpectActualAnalyzer,
    private val coroutineLeakDetector: CoroutineLeakDetector,
    private val wasmThreadSafetyAnalyzer: WasmThreadSafetyAnalyzer,
    private val apiMisuseAnalyzer: ApiMisuseAnalyzer
) {
    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()
    
    private val _diagnostics = MutableStateFlow<List<Diagnostic>>(emptyList())
    val diagnostics: StateFlow<List<Diagnostic>> = _diagnostics.asStateFlow()
    
    private val analyzers: List<Analyzer> = listOf(
        expectActualAnalyzer,
        coroutineLeakDetector,
        wasmThreadSafetyAnalyzer,
        apiMisuseAnalyzer
    )
    
    /**
     * Runs all analyzers on the given repository.
     * Results are aggregated and deduplicated.
     */
    suspend fun analyze(repoPath: String): DiagnosticReport {
        val startTime = Clock.System.now().toEpochMilliseconds()
        val allDiagnostics = mutableListOf<Diagnostic>()
        val analyzerResults = mutableMapOf<String, AnalyzerResult>()
        
        try {
            _analysisState.value = AnalysisState.Running(
                totalAnalyzers = analyzers.size,
                completedAnalyzers = 0,
                currentAnalyzer = null
            )
            
            analyzers.forEachIndexed { index, analyzer ->
                _analysisState.value = AnalysisState.Running(
                    totalAnalyzers = analyzers.size,
                    completedAnalyzers = index,
                    currentAnalyzer = analyzer.name
                )
                
                val analyzerStart = Clock.System.now().toEpochMilliseconds()
                
                try {
                    val diagnostics = analyzer.analyze(repoPath)
                    allDiagnostics.addAll(diagnostics)
                    
                    analyzerResults[analyzer.name] = AnalyzerResult(
                        analyzerName = analyzer.name,
                        diagnosticCount = diagnostics.size,
                        durationMs = Clock.System.now().toEpochMilliseconds() - analyzerStart,
                        success = true,
                        error = null
                    )
                } catch (e: Exception) {
                    analyzerResults[analyzer.name] = AnalyzerResult(
                        analyzerName = analyzer.name,
                        diagnosticCount = 0,
                        durationMs = Clock.System.now().toEpochMilliseconds() - analyzerStart,
                        success = false,
                        error = e.message
                    )
                }
            }
            
            // Deduplicate diagnostics (same location + message)
            val deduplicated = deduplicateDiagnostics(allDiagnostics)
            
            // Sort by severity, then by file, then by line
            val sorted = deduplicated.sortedWith(
                compareBy(
                    { it.severity.ordinal },
                    { it.location.filePath },
                    { it.location.startLine }
                )
            )
            
            _diagnostics.value = sorted
            
            val report = DiagnosticReport(
                repoPath = repoPath,
                totalDiagnostics = sorted.size,
                bySeverity = sorted.groupBy { it.severity }.mapValues { it.value.size },
                byCategory = sorted.groupBy { it.category }.mapValues { it.value.size },
                analyzerResults = analyzerResults.values.toList(),
                durationMs = Clock.System.now().toEpochMilliseconds() - startTime,
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
            
            _analysisState.value = AnalysisState.Completed(report)
            
            return report
            
        } catch (e: Exception) {
            _analysisState.value = AnalysisState.Failed(e.message ?: "Unknown error")
            throw e
        }
    }
    
    /**
     * Gets diagnostics for a specific file.
     */
    fun getDiagnosticsForFile(filePath: String): List<Diagnostic> {
        return _diagnostics.value.filter { it.location.filePath == filePath }
    }
    
    /**
     * Gets diagnostics filtered by severity.
     */
    fun getDiagnosticsBySeverity(severity: DiagnosticSeverity): List<Diagnostic> {
        return _diagnostics.value.filter { it.severity == severity }
    }
    
    /**
     * Gets diagnostics filtered by category.
     */
    fun getDiagnosticsByCategory(category: DiagnosticCategory): List<Diagnostic> {
        return _diagnostics.value.filter { it.category == category }
    }
    
    /**
     * Gets diagnostics that have auto-fixes available.
     */
    fun getFixableDiagnostics(): List<Diagnostic> {
        return _diagnostics.value.filter { it.fixes.isNotEmpty() }
    }
    
    /**
     * Marks a diagnostic as resolved/dismissed.
     */
    fun dismissDiagnostic(diagnosticId: String) {
        _diagnostics.value = _diagnostics.value.map { diagnostic ->
            if (diagnostic.id == diagnosticId) {
                diagnostic.copy(isActive = false)
            } else {
                diagnostic
            }
        }
    }
    
    /**
     * Clears all diagnostics.
     */
    fun clearDiagnostics() {
        _diagnostics.value = emptyList()
        _analysisState.value = AnalysisState.Idle
    }
    
    private fun deduplicateDiagnostics(diagnostics: List<Diagnostic>): List<Diagnostic> {
        val seen = mutableSetOf<String>()
        return diagnostics.filter { diagnostic ->
            val key = "${diagnostic.location.filePath}:${diagnostic.location.startLine}:${diagnostic.message}"
            if (key in seen) {
                false
            } else {
                seen.add(key)
                true
            }
        }
    }
}

/**
 * State of the diagnostic analysis process.
 */
sealed class AnalysisState {
    data object Idle : AnalysisState()
    
    data class Running(
        val totalAnalyzers: Int,
        val completedAnalyzers: Int,
        val currentAnalyzer: String?
    ) : AnalysisState() {
        val progress: Float get() = completedAnalyzers.toFloat() / totalAnalyzers
    }
    
    data class Completed(val report: DiagnosticReport) : AnalysisState()
    
    data class Failed(val error: String) : AnalysisState()
}

/**
 * Summary report of a diagnostic run.
 */
data class DiagnosticReport(
    val repoPath: String,
    val totalDiagnostics: Int,
    val bySeverity: Map<DiagnosticSeverity, Int>,
    val byCategory: Map<DiagnosticCategory, Int>,
    val analyzerResults: List<AnalyzerResult>,
    val durationMs: Long,
    val timestamp: Long
) {
    val errorCount: Int get() = bySeverity[DiagnosticSeverity.ERROR] ?: 0
    val warningCount: Int get() = bySeverity[DiagnosticSeverity.WARNING] ?: 0
    val infoCount: Int get() = bySeverity[DiagnosticSeverity.INFO] ?: 0
    val hintCount: Int get() = bySeverity[DiagnosticSeverity.HINT] ?: 0
}

/**
 * Result of running a single analyzer.
 */
data class AnalyzerResult(
    val analyzerName: String,
    val diagnosticCount: Int,
    val durationMs: Long,
    val success: Boolean,
    val error: String?
)
