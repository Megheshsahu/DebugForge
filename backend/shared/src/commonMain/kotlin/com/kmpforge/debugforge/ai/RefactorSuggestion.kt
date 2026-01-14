package com.kmpforge.debugforge.ai

import kotlinx.serialization.Serializable
import com.kmpforge.debugforge.diagnostics.DiagnosticLocation

/**
 * An AI-generated refactoring suggestion with a git-style diff.
 * Every suggestion includes rationale and confidence score as required.
 */
@Serializable
data class RefactorSuggestion(
    /** Unique identifier for this suggestion */
    val id: String,
    
    /** Short title describing the refactoring */
    val title: String,
    
    /** Detailed rationale explaining why this refactoring is suggested */
    val rationale: String,
    
    /** Confidence score from 0.0 to 1.0 */
    val confidence: Float,
    
    /** Category of refactoring */
    val category: RefactorCategory,
    
    /** Priority level */
    val priority: RefactorPriority,
    
    /** The unified diff in git format */
    val unifiedDiff: String,
    
    /** Structured representation of changes */
    val changes: List<FileChange>,
    
    /** Affected source locations */
    val affectedLocations: List<DiagnosticLocation>,
    
    /** Diagnostics this refactoring would fix */
    val fixesDiagnosticIds: List<String>,
    
    /** Estimated impact on shared code percentage */
    val sharedCodeImpact: Float?,
    
    /** Whether this refactoring can be auto-applied */
    val isAutoApplicable: Boolean,
    
    /** Potential risks of applying this refactoring */
    val risks: List<RefactorRisk>,
    
    /** Source of this suggestion */
    val source: RefactorSource,
    
    /** Timestamp when this suggestion was generated */
    val generatedAt: Long
)

@Serializable
enum class RefactorCategory {
    EXPECT_ACTUAL_FIX,      // Fix expect/actual issues
    COROUTINE_SAFETY,       // Fix coroutine safety issues
    WASM_COMPATIBILITY,     // Make code Wasm-compatible
    CODE_SHARING,           // Increase shared code
    PERFORMANCE,            // Performance optimization
    MODERNIZATION,          // Update to newer APIs
    CLEANUP                 // General code cleanup
}

@Serializable
enum class RefactorPriority {
    CRITICAL,   // Must fix for correctness
    HIGH,       // Should fix soon
    MEDIUM,     // Good to fix
    LOW         // Nice to have
}

/**
 * A change to a single file.
 */
@Serializable
data class FileChange(
    /** Path to the file */
    val filePath: String,
    
    /** Type of change */
    val changeType: ChangeType,
    
    /** Hunks (sections) of changes */
    val hunks: List<DiffHunk>
)

@Serializable
enum class ChangeType {
    MODIFIED,
    ADDED,
    DELETED,
    RENAMED
}

/**
 * A hunk in a unified diff.
 */
@Serializable
data class DiffHunk(
    /** Starting line in the original file */
    val originalStart: Int,
    
    /** Number of lines in the original file */
    val originalCount: Int,
    
    /** Starting line in the modified file */
    val modifiedStart: Int,
    
    /** Number of lines in the modified file */
    val modifiedCount: Int,
    
    /** Lines in this hunk */
    val lines: List<DiffLine>
)

/**
 * A single line in a diff.
 */
@Serializable
data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val originalLineNumber: Int?,
    val modifiedLineNumber: Int?
)

@Serializable
enum class DiffLineType {
    CONTEXT,    // Unchanged line for context
    ADDITION,   // Added line (+)
    DELETION    // Removed line (-)
}

/**
 * A potential risk of applying a refactoring.
 */
@Serializable
data class RefactorRisk(
    val type: RiskType,
    val description: String,
    val severity: RiskSeverity
)

@Serializable
enum class RiskType {
    BREAKING_CHANGE,        // May break API consumers
    BEHAVIOR_CHANGE,        // May change runtime behavior
    PERFORMANCE_IMPACT,     // May affect performance
    TEST_FAILURE,           // May cause test failures
    COMPILATION_ERROR       // May cause compilation errors
}

@Serializable
enum class RiskSeverity {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * Source of the refactoring suggestion.
 */
@Serializable
enum class RefactorSource {
    RULE_ENGINE,        // Rule-based engine
    ML_INFERENCE,       // Machine learning inference
    USER_REQUESTED      // User explicitly requested
}
