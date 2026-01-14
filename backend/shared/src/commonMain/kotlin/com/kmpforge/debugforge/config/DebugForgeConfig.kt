package com.kmpforge.debugforge.config

import kotlinx.serialization.Serializable

/**
 * Configuration for DebugForge.
 * All settings are stored locally and can be serialized.
 */
@Serializable
data class DebugForgeConfig(
    /**
     * Path to the database file.
     * If null, uses in-memory database.
     */
    val databasePath: String? = null,
    
    /**
     * Whether to enable verbose logging.
     */
    val verboseLogging: Boolean = false,
    
    /**
     * Maximum number of files to analyze in parallel.
     */
    val maxParallelFiles: Int = 10,
    
    /**
     * Whether to enable ML-based inference.
     * Falls back to decision tree if models not available.
     */
    val enableMlInference: Boolean = false,
    
    /**
     * Maximum file size in bytes to analyze.
     * Files larger than this are skipped.
     */
    val maxFileSizeBytes: Long = 10 * 1024 * 1024, // 10MB
    
    /**
     * Analyzers to run.
     */
    val analyzers: AnalyzerConfig = AnalyzerConfig(),
    
    /**
     * Preview configuration.
     */
    val preview: PreviewConfig = PreviewConfig(),
    
    /**
     * Git configuration.
     */
    val git: GitConfig = GitConfig()
)

/**
 * Configuration for analyzers.
 */
@Serializable
data class AnalyzerConfig(
    val enableExpectActualAnalyzer: Boolean = true,
    val enableCoroutineLeakDetector: Boolean = true,
    val enableWasmThreadSafetyAnalyzer: Boolean = true,
    val enableApiMisuseAnalyzer: Boolean = true,
    
    /**
     * Diagnostic severities to report.
     * If empty, all severities are reported.
     */
    val severityFilter: Set<String> = emptySet(),
    
    /**
     * File patterns to include in analysis.
     * Glob patterns relative to repository root.
     */
    val includePatterns: List<String> = listOf("**/*.kt", "**/*.kts"),
    
    /**
     * File patterns to exclude from analysis.
     */
    val excludePatterns: List<String> = listOf(
        "**/build/**",
        "**/.gradle/**",
        "**/node_modules/**"
    )
)

/**
 * Configuration for preview system.
 */
@Serializable
data class PreviewConfig(
    /**
     * Port for desktop preview server.
     */
    val desktopPort: Int = 9100,
    
    /**
     * Port for browser/WASM preview.
     */
    val browserPort: Int = 9200,
    
    /**
     * Whether to enable hot reload.
     */
    val enableHotReload: Boolean = true,
    
    /**
     * Debounce delay for hot reload in milliseconds.
     */
    val hotReloadDebounceMs: Long = 500,
    
    /**
     * iOS Simulator device name for iOS previews.
     */
    val iosSimulatorDevice: String = "iPhone 15 Pro",
    
    /**
     * Android emulator name for Android previews.
     */
    val androidEmulator: String = "Pixel_7_API_34"
)

/**
 * Configuration for Git operations.
 */
@Serializable
data class GitConfig(
    /**
     * Default branch to clone.
     */
    val defaultBranch: String = "main",
    
    /**
     * Whether to clone with depth 1 (shallow clone).
     */
    val shallowClone: Boolean = true,
    
    /**
     * Clone timeout in seconds.
     */
    val cloneTimeoutSeconds: Int = 300,
    
    /**
     * Whether to automatically fetch updates.
     */
    val autoFetch: Boolean = false
)

/**
 * Builder for DebugForgeConfig.
 */
class DebugForgeConfigBuilder {
    private var databasePath: String? = null
    private var verboseLogging = false
    private var maxParallelFiles = 10
    private var enableMlInference = false
    private var maxFileSizeBytes = 10L * 1024 * 1024
    private var analyzers = AnalyzerConfig()
    private var preview = PreviewConfig()
    private var git = GitConfig()
    
    fun databasePath(path: String?) = apply { databasePath = path }
    fun verboseLogging(enabled: Boolean) = apply { verboseLogging = enabled }
    fun maxParallelFiles(count: Int) = apply { maxParallelFiles = count }
    fun enableMlInference(enabled: Boolean) = apply { enableMlInference = enabled }
    fun maxFileSizeBytes(bytes: Long) = apply { maxFileSizeBytes = bytes }
    fun analyzers(config: AnalyzerConfig) = apply { analyzers = config }
    fun preview(config: PreviewConfig) = apply { preview = config }
    fun git(config: GitConfig) = apply { git = config }
    
    fun build() = DebugForgeConfig(
        databasePath = databasePath,
        verboseLogging = verboseLogging,
        maxParallelFiles = maxParallelFiles,
        enableMlInference = enableMlInference,
        maxFileSizeBytes = maxFileSizeBytes,
        analyzers = analyzers,
        preview = preview,
        git = git
    )
}

fun debugForgeConfig(block: DebugForgeConfigBuilder.() -> Unit): DebugForgeConfig {
    return DebugForgeConfigBuilder().apply(block).build()
}
