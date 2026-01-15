package com.kmpforge.debugforge.metrics

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

/**
 * Metrics tracking for DebugForge operations.
 * All metrics are stored locally.
 */
@Serializable
data class OperationMetrics(
    val operationId: String,
    val operationType: OperationType,
    val startTime: Instant,
    val endTime: Instant? = null,
    val success: Boolean = false,
    val errorMessage: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    val durationMs: Long?
        get() = endTime?.let { it.toEpochMilliseconds() - startTime.toEpochMilliseconds() }
}

@Serializable
enum class OperationType {
    REPO_LOAD,
    REPO_CLONE,
    PROJECT_INDEX,
    DIAGNOSTIC_RUN,
    REFACTOR_GENERATE,
    REFACTOR_APPLY,
    PREVIEW_START,
    PREVIEW_STOP,
    DIFF_GENERATE,
    DIFF_APPLY
}

/**
 * Repository statistics.
 */
@Serializable
data class RepoStatistics(
    val repoPath: String,
    val totalFiles: Int,
    val sourceFiles: Int,
    val totalLinesOfCode: Int,
    val moduleCount: Int,
    val targetCount: Int,
    val commonMainLoc: Int,
    val platformMainLoc: Int,
    val sharedCodePercentage: Float,
    val lastIndexedAt: Instant,
    val indexDurationMs: Long
)

/**
 * Analysis statistics.
 */
@Serializable
data class AnalysisStatistics(
    val repoPath: String,
    val analyzersRun: Int,
    val diagnosticsFound: Int,
    val criticalCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val hintCount: Int,
    val analysisDurationMs: Long,
    val filesAnalyzed: Int
)

/**
 * Refactoring statistics.
 */
@Serializable
data class RefactoringStatistics(
    val suggestionsGenerated: Int,
    val suggestionsApplied: Int,
    val suggestionsDismissed: Int,
    val averageConfidence: Float,
    val categoryBreakdown: Map<String, Int>,
    val priorityBreakdown: Map<String, Int>
)

/**
 * Collector for metrics.
 */
class MetricsCollector {
    private val operations = mutableListOf<OperationMetrics>()
    private var repoStats: RepoStatistics? = null
    private var analysisStats: AnalysisStatistics? = null
    private var refactoringStats: RefactoringStatistics? = null
    
    fun recordOperation(metrics: OperationMetrics) {
        operations.add(metrics)
        // Keep only last 1000 operations
        if (operations.size > 1000) {
            operations.removeAt(0)
        }
    }
    
    fun updateRepoStatistics(stats: RepoStatistics) {
        repoStats = stats
    }
    
    fun updateAnalysisStatistics(stats: AnalysisStatistics) {
        analysisStats = stats
    }
    
    fun updateRefactoringStatistics(stats: RefactoringStatistics) {
        refactoringStats = stats
    }
    
    fun getRecentOperations(count: Int = 100): List<OperationMetrics> {
        return operations.takeLast(count)
    }
    
    fun getOperationsByType(type: OperationType, count: Int = 100): List<OperationMetrics> {
        return operations.filter { it.operationType == type }.takeLast(count)
    }
    
    fun getAverageDuration(type: OperationType): Long? {
        val durations = operations
            .filter { it.operationType == type && it.durationMs != null }
            .mapNotNull { it.durationMs }
        
        return if (durations.isNotEmpty()) {
            durations.average().toLong()
        } else null
    }
    
    fun getSuccessRate(type: OperationType): Float {
        val ops = operations.filter { it.operationType == type }
        if (ops.isEmpty()) return 0f
        return ops.count { it.success }.toFloat() / ops.size
    }
    
    fun getRepoStatistics(): RepoStatistics? = repoStats
    fun getAnalysisStatistics(): AnalysisStatistics? = analysisStats
    fun getRefactoringStatistics(): RefactoringStatistics? = refactoringStats
    
    fun clear() {
        operations.clear()
        repoStats = null
        analysisStats = null
        refactoringStats = null
    }
    
    /**
     * Generate summary of all metrics.
     */
    fun generateSummary(): MetricsSummary {
        val byType = OperationType.entries.associateWith { type ->
            OperationTypeSummary(
                count = operations.count { it.operationType == type },
                successRate = getSuccessRate(type),
                averageDurationMs = getAverageDuration(type)
            )
        }
        
        return MetricsSummary(
            totalOperations = operations.size,
            operationsByType = byType,
            repoStatistics = repoStats,
            analysisStatistics = analysisStats,
            refactoringStatistics = refactoringStats
        )
    }
}

@Serializable
data class OperationTypeSummary(
    val count: Int,
    val successRate: Float,
    val averageDurationMs: Long?
)

@Serializable
data class MetricsSummary(
    val totalOperations: Int,
    val operationsByType: Map<OperationType, OperationTypeSummary>,
    val repoStatistics: RepoStatistics?,
    val analysisStatistics: AnalysisStatistics?,
    val refactoringStatistics: RefactoringStatistics?
)
