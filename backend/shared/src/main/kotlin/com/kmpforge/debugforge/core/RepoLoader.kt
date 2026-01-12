package com.kmpforge.debugforge.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Loads and parses KMP repositories from local filesystem or Git.
 * Platform-specific implementations handle actual Git operations.
 */
interface RepoLoader {
    /**
     * Current loading state.
     */
    val loadingState: StateFlow<LoadingState>
    
    /**
     * Loads a repository from a local directory path.
     * 
     * @param path Absolute path to the repository root
     * @return Result containing the parsed repository or error
     */
    suspend fun loadLocalRepository(path: String): Result<ParsedRepository>
    
    /**
     * Clones a Git repository and loads it.
     * Platform-specific implementation (JGit on JVM, libgit2 on native).
     * 
     * @param url Git repository URL
     * @param targetPath Local path to clone to
     * @param branch Branch to checkout (null for default)
     * @return Result containing the parsed repository or error
     */
    suspend fun cloneAndLoad(
        url: String,
        targetPath: String,
        branch: String? = null
    ): Result<ParsedRepository>
    
    /**
     * Refreshes a previously loaded repository.
     * Detects changes since last load.
     * 
     * @param repoPath Path to the repository
     * @return Result containing changed files
     */
    suspend fun refresh(repoPath: String): Result<RepositoryDelta>
    
    /**
     * Validates that a path is a valid KMP repository.
     */
    suspend fun validateRepository(path: String): ValidationResult
}

/**
 * State of the repository loading process.
 */
sealed class LoadingState {
    data object Idle : LoadingState()
    data class InProgress(
        val operation: String,
        val progress: Float,
        val currentFile: String?
    ) : LoadingState()
    data class Completed(val repoPath: String) : LoadingState()
    data class Error(val message: String, val cause: Throwable?) : LoadingState()
}

/**
 * A parsed repository ready for indexing.
 */
data class ParsedRepository(
    /** Repository root path */
    val rootPath: String,
    
    /** Repository name (directory name or Git repo name) */
    val name: String,
    
    /** Detected Gradle modules */
    val modules: List<DetectedModule>,
    
    /** Root build configuration */
    val rootBuildConfig: RootBuildConfig,
    
    /** All Kotlin source files */
    val kotlinFiles: List<SourceFile>,
    
    /** All resource files */
    val resourceFiles: List<ResourceFile>,
    
    /** Git information (if available) */
    val gitInfo: GitInfo?,
    
    /** Parse timestamp */
    val parsedAt: Long
)

/**
 * A module detected during repository parsing.
 */
data class DetectedModule(
    val path: String,
    val gradlePath: String,
    val name: String,
    val buildFilePath: String,
    val buildFileType: BuildSystem,
    val sourceSets: List<DetectedSourceSet>
)

/**
 * A source set detected in a module.
 */
data class DetectedSourceSet(
    val name: String,
    val platform: SourceSetPlatform,
    val kotlinPath: String?,
    val javaPath: String?,
    val resourcePath: String?,
    val files: List<SourceFile>
)

/**
 * A Kotlin source file.
 */
data class SourceFile(
    val absolutePath: String,
    val relativePath: String,
    val moduleGradlePath: String,
    val sourceSetName: String,
    val packageName: String?,
    val content: String,
    val hash: String,
    val lastModified: Long,
    val lineCount: Int
)

/**
 * A resource file.
 */
data class ResourceFile(
    val absolutePath: String,
    val relativePath: String,
    val moduleGradlePath: String,
    val sourceSetName: String,
    val type: ResourceType
)

enum class ResourceType {
    XML, JSON, PROPERTIES, IMAGE, FONT, OTHER
}

/**
 * Root-level build configuration.
 */
data class RootBuildConfig(
    val gradleVersion: String?,
    val kotlinVersion: String?,
    val agpVersion: String?,
    val composeVersion: String?,
    val plugins: List<String>,
    val repositories: List<String>
)

/**
 * Git repository information.
 */
data class GitInfo(
    val remoteUrl: String?,
    val currentBranch: String,
    val headCommit: String,
    val isDirty: Boolean,
    val lastCommitMessage: String?,
    val lastCommitAuthor: String?,
    val lastCommitTimestamp: Long?
)

/**
 * Changes detected in a repository since last load.
 */
data class RepositoryDelta(
    val addedFiles: List<String>,
    val modifiedFiles: List<String>,
    val deletedFiles: List<String>,
    val hasStructuralChanges: Boolean
)

/**
 * Result of repository validation.
 */
data class ValidationResult(
    val isValid: Boolean,
    val isKmpProject: Boolean,
    val hasGradle: Boolean,
    val hasKotlin: Boolean,
    val issues: List<String>,
    val suggestions: List<String>
)
