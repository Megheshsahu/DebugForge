package com.kmpforge.debugforge.diagnostics

import kotlinx.serialization.Serializable

/**
 * Represents a diagnostic issue detected by any analyzer.
 * This is the unified diagnostic format consumed by the frontend.
 */
@Serializable
data class Diagnostic(
    /** Unique identifier for this diagnostic */
    val id: String,
    
    /** Diagnostic severity level */
    val severity: DiagnosticSeverity,
    
    /** Category of the diagnostic */
    val category: DiagnosticCategory,
    
    /** Human-readable message */
    val message: String,
    
    /** Detailed explanation of the issue */
    val explanation: String,
    
    /** Code snippet showing the problematic code */
    val codeSnippet: String? = null,
    
    /** Location where the issue was detected */
    val location: DiagnosticLocation,
    
    /** Related locations (e.g., expect declaration for missing actual) */
    val relatedLocations: List<DiagnosticLocation> = emptyList(),
    
    /** Suggested fixes */
    val fixes: List<DiagnosticFix> = emptyList(),
    
    /** Analyzer that produced this diagnostic */
    val source: AnalyzerSource,
    
    /** Timestamp when this diagnostic was created */
    val timestamp: Long,
    
    /** Whether this diagnostic is still active */
    val isActive: Boolean = true,
    
    /** Tags for filtering */
    val tags: Set<DiagnosticTag> = emptySet()
)

@Serializable
enum class DiagnosticSeverity {
    ERROR,      // Must be fixed for correct behavior
    WARNING,    // Should be fixed, may cause issues
    INFO,       // Informational, suggestion for improvement
    HINT        // Low-priority suggestions
}

@Serializable
enum class DiagnosticCategory {
    EXPECT_ACTUAL,          // expect/actual issues
    COROUTINE_SAFETY,       // Coroutine-related issues
    WASM_THREADING,         // Wasm threading violations
    API_MISUSE,             // API misuse patterns
    PERFORMANCE,            // Performance concerns
    MEMORY,                 // Memory-related issues
    COMPATIBILITY,          // Cross-platform compatibility
    DEPRECATION,            // Deprecated API usage
    STYLE                   // Code style issues
}

/**
 * Location of a diagnostic in source code.
 */
@Serializable
data class DiagnosticLocation(
    /** Absolute path to the file */
    val filePath: String,
    
    /** File path relative to module root */
    val relativeFilePath: String,
    
    /** Module ID containing this file */
    val moduleId: String,
    
    /** Source set name */
    val sourceSet: String,
    
    /** Starting line (1-indexed) */
    val startLine: Int,
    
    /** Starting column (1-indexed) */
    val startColumn: Int,
    
    /** Ending line (1-indexed) */
    val endLine: Int,
    
    /** Ending column (1-indexed) */
    val endColumn: Int,
    
    /** The actual source code at this location */
    val sourceSnippet: String? = null
)

/**
 * A suggested fix for a diagnostic.
 */
@Serializable
data class DiagnosticFix(
    /** Short description of the fix */
    val title: String,
    
    /** Detailed description */
    val description: String,
    
    /** Text edits to apply */
    val edits: List<TextEdit>,
    
    /** Whether this fix is safe to auto-apply */
    val isPreferred: Boolean,
    
    /** Confidence in this fix (0-100) */
    val confidence: Float
)

/**
 * A text edit operation.
 */
@Serializable
data class TextEdit(
    /** File path */
    val filePath: String,
    
    /** Range to replace */
    val range: TextRange,
    
    /** New text (empty string for deletion) */
    val newText: String
)

@Serializable
data class TextRange(
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int
)

/**
 * Identifies which analyzer produced a diagnostic.
 */
@Serializable
enum class AnalyzerSource {
    EXPECT_ACTUAL_ANALYZER,
    COROUTINE_LEAK_DETECTOR,
    WASM_THREAD_SAFETY_ANALYZER,
    API_MISUSE_ANALYZER,
    REFACTOR_ENGINE
}

/**
 * Tags for additional diagnostic categorization.
 */
@Serializable
enum class DiagnosticTag {
    FIXABLE,            // Has an auto-fix available
    CROSS_PLATFORM,     // Affects multiple platforms
    BREAKING_CHANGE,    // Fix might be a breaking change
    SECURITY,           // Security-related
    DEPRECATED,         // Related to deprecation
    NEW_IN_VERSION      // New diagnostic in current version
}
