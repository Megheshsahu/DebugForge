package com.kmpforge.debugforge.core

import kotlinx.serialization.Serializable

/**
 * Aggregated metrics about shared code coverage across the KMP project.
 * These metrics help developers understand how much code is truly shared
 * versus platform-specific.
 */
@Serializable
data class SharedCodeMetrics(
    /** Total lines of Kotlin code across all modules */
    val totalLinesOfCode: Int,
    
    /** Lines of code in common source sets */
    val sharedLinesOfCode: Int,
    
    /** Percentage of code that is shared (0-100) */
    val sharedCodePercentage: Float,
    
    /** Breakdown by platform */
    val platformBreakdown: PlatformCodeBreakdown,
    
    /** Number of expect declarations */
    val expectDeclarations: Int,
    
    /** Number of actual implementations */
    val actualImplementations: Int,
    
    /** expect/actual coverage ratio */
    val expectActualCoverage: Float,
    
    /** Modules sorted by shared code percentage */
    val moduleRankings: List<ModuleSharedRanking>,
    
    /** Files that could potentially be moved to common */
    val sharableCandidates: List<SharableCandidate>
) {
    companion object {
        val EMPTY = SharedCodeMetrics(
            totalLinesOfCode = 0,
            sharedLinesOfCode = 0,
            sharedCodePercentage = 0f,
            platformBreakdown = PlatformCodeBreakdown.EMPTY,
            expectDeclarations = 0,
            actualImplementations = 0,
            expectActualCoverage = 0f,
            moduleRankings = emptyList(),
            sharableCandidates = emptyList()
        )
    }
}

/**
 * Breakdown of code distribution across platforms.
 */
@Serializable
data class PlatformCodeBreakdown(
    val commonLines: Int,
    val androidLines: Int,
    val iosLines: Int,
    val jvmLines: Int,
    val jsLines: Int,
    val wasmLines: Int,
    val nativeLines: Int
) {
    companion object {
        val EMPTY = PlatformCodeBreakdown(0, 0, 0, 0, 0, 0, 0)
    }
    
    val total: Int get() = commonLines + androidLines + iosLines + jvmLines + jsLines + wasmLines + nativeLines
}

/**
 * Ranking of a module by shared code percentage.
 */
@Serializable
data class ModuleSharedRanking(
    val moduleId: String,
    val moduleName: String,
    val sharedPercentage: Float,
    val sharedLines: Int,
    val totalLines: Int,
    val rank: Int
)

/**
 * Represents a file that could potentially be moved to common source set.
 */
@Serializable
data class SharableCandidate(
    /** File path relative to module */
    val filePath: String,
    
    /** Current source set */
    val currentSourceSet: String,
    
    /** Confidence that this file can be shared (0-100) */
    val confidence: Float,
    
    /** Reasons why this file might be sharable */
    val reasons: List<String>,
    
    /** Blockers preventing sharing */
    val blockers: List<SharingBlocker>
)

/**
 * A specific issue preventing code from being shared.
 */
@Serializable
data class SharingBlocker(
    val type: BlockerType,
    val description: String,
    val lineNumber: Int?,
    val symbol: String?
)

@Serializable
enum class BlockerType {
    PLATFORM_API_USAGE,      // Uses platform-specific APIs
    JVM_ONLY_LIBRARY,        // Depends on JVM-only library
    ANDROID_FRAMEWORK,       // Uses Android framework classes
    IOS_FRAMEWORK,           // Uses iOS/Darwin frameworks
    REFLECTION,              // Uses reflection not available on all targets
    THREADING,               // Uses platform-specific threading
    FILE_IO,                 // Uses platform-specific file I/O
    NATIVE_INTEROP           // Uses native interop
}
