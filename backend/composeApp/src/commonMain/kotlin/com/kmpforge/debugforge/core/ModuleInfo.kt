package com.kmpforge.debugforge.core

import kotlinx.serialization.Serializable

/**
 * Represents a KMP module detected in the repository.
 * A module corresponds to a Gradle subproject with Kotlin Multiplatform configuration.
 */
@Serializable
data class ModuleInfo(
    /** Unique identifier for this module (Gradle path, e.g., ":shared:core") */
    val id: String,
    
    /** Human-readable module name */
    val name: String,
    
    /** Absolute path to the module directory */
    val path: String,
    
    /** Gradle path (e.g., ":shared:core") */
    val gradlePath: String,
    
    /** Source sets detected in this module */
    val sourceSets: List<SourceSetInfo>,
    
    /** Dependencies on other modules */
    val dependencies: List<ModuleDependency>,
    
    /** KMP targets configured for this module */
    val targets: Set<KmpTarget>,
    
    /** Whether this module contains shared (common) code */
    val hasCommonCode: Boolean,
    
    /** Build configuration detected */
    val buildConfig: BuildConfig,
    
    /** File statistics for this module */
    val fileStats: FileStats
)

/**
 * Information about a single source set within a module.
 */
@Serializable
data class SourceSetInfo(
    /** Source set name (e.g., "commonMain", "androidMain", "iosMain") */
    val name: String,
    
    /** Platform this source set targets */
    val platform: SourceSetPlatform,
    
    /** Path to source files */
    val sourcePath: String,
    
    /** Path to resources */
    val resourcePath: String?,
    
    /** Number of Kotlin files */
    val kotlinFileCount: Int,
    
    /** Total lines of Kotlin code */
    val kotlinLinesOfCode: Int,
    
    /** Dependencies declared for this source set */
    val dependencies: List<String>
)

/**
 * Categorizes source sets by their platform scope.
 */
@Serializable
enum class SourceSetPlatform {
    COMMON,      // commonMain, commonTest
    ANDROID,     // androidMain
    IOS,         // iosMain, iosX64Main, etc.
    JVM,         // jvmMain, desktopMain
    JS,          // jsMain
    WASM,        // wasmJsMain
    NATIVE,      // nativeMain (intermediate)
    MACOS,       // macosMain
    LINUX,       // linuxMain
    WINDOWS,     // mingwMain
    UNKNOWN
}

/**
 * Represents a dependency between KMP modules.
 */
@Serializable
data class ModuleDependency(
    /** Target module ID */
    val targetModuleId: String,
    
    /** Dependency configuration (implementation, api, etc.) */
    val configuration: DependencyConfiguration,
    
    /** Whether this is a test-only dependency */
    val isTestDependency: Boolean
)

@Serializable
enum class DependencyConfiguration {
    IMPLEMENTATION,
    API,
    COMPILE_ONLY,
    RUNTIME_ONLY
}

/**
 * KMP compilation targets.
 */
@Serializable
enum class KmpTarget {
    ANDROID,
    IOS_ARM64,
    IOS_X64,
    IOS_SIMULATOR_ARM64,
    JVM,
    JS_IR,
    WASM_JS,
    MACOS_ARM64,
    MACOS_X64,
    LINUX_X64,
    MINGW_X64
}

/**
 * Build configuration for a module.
 */
@Serializable
data class BuildConfig(
    /** Build system used (Gradle KTS, Gradle Groovy) */
    val buildSystem: BuildSystem,
    
    /** Kotlin version configured */
    val kotlinVersion: String?,
    
    /** Whether Compose Multiplatform is enabled */
    val composeEnabled: Boolean,
    
    /** Whether SQLDelight is configured */
    val sqlDelightEnabled: Boolean,
    
    /** KSP processors configured */
    val kspProcessors: List<String>
)

@Serializable
enum class BuildSystem {
    GRADLE_KTS,
    GRADLE_GROOVY,
    UNKNOWN
}

/**
 * File statistics for a module.
 */
@Serializable
data class FileStats(
    val totalFiles: Int,
    val kotlinFiles: Int,
    val javaFiles: Int,
    val resourceFiles: Int,
    val totalLinesOfCode: Int,
    val kotlinLinesOfCode: Int
)
