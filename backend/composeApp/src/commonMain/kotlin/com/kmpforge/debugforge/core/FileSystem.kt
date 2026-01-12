package com.kmpforge.debugforge.core

/**
 * Platform-agnostic file system abstraction.
 * Implemented differently on JVM, Native, and JS platforms.
 */
interface FileSystem {
    /**
     * Checks if a file or directory exists.
     */
    suspend fun exists(path: String): Boolean
    
    /**
     * Checks if path is a directory.
     */
    suspend fun isDirectory(path: String): Boolean
    
    /**
     * Checks if path is a file.
     */
    suspend fun isFile(path: String): Boolean
    
    /**
     * Reads the entire content of a file as a string.
     */
    suspend fun readFile(path: String): String
    
    /**
     * Reads the entire content of a file as bytes.
     */
    suspend fun readFileBytes(path: String): ByteArray
    
    /**
     * Writes content to a file, creating it if necessary.
     */
    suspend fun writeFile(path: String, content: String)
    
    /**
     * Writes bytes to a file, creating it if necessary.
     */
    suspend fun writeFileBytes(path: String, content: ByteArray)
    
    /**
     * Deletes a file.
     */
    suspend fun deleteFile(path: String)
    
    /**
     * Creates a directory and all parent directories.
     */
    suspend fun createDirectory(path: String)
    
    /**
     * Deletes a directory recursively.
     */
    suspend fun deleteDirectory(path: String)
    
    /**
     * Lists immediate children of a directory.
     * Returns file/directory names, not full paths.
     */
    suspend fun listDirectory(path: String): List<String>
    
    /**
     * Recursively walks a directory and returns all file paths.
     * @param filter Optional filter for which files to include
     */
    suspend fun walkDirectory(path: String, filter: (String) -> Boolean = { true }): List<String>
    
    /**
     * Computes a hash of file content for change detection.
     */
    suspend fun computeFileHash(path: String): String
    
    /**
     * Gets the file size in bytes.
     */
    suspend fun getFileSize(path: String): Long
    
    /**
     * Gets the last modification timestamp.
     */
    suspend fun getLastModified(path: String): Long
    
    /**
     * Copies a file from source to destination.
     */
    suspend fun copy(source: String, destination: String)
    
    /**
     * Moves a file from source to destination.
     */
    suspend fun move(source: String, destination: String)
    
    /**
     * Gets absolute path.
     */
    suspend fun getAbsolutePath(path: String): String
    
    /**
     * Gets relative path from base to target.
     */
    suspend fun getRelativePath(base: String, path: String): String
    
    /**
     * Gets the parent directory path.
     */
    suspend fun getParent(path: String): String?
    
    /**
     * Gets the file name from a path.
     */
    suspend fun getFileName(path: String): String
    
    /**
     * Normalizes path separators.
     */
    suspend fun normalizePath(path: String): String
    
    /**
     * Resolves a path relative to a base path.
     */
    suspend fun resolvePath(base: String, relative: String): String
    
    /**
     * Creates a temporary file.
     */
    suspend fun createTempFile(prefix: String, suffix: String): String
    
    /**
     * Creates a temporary directory.
     */
    suspend fun createTempDirectory(prefix: String): String
}

/**
 * Platform-agnostic Git operations.
 * Implemented using JGit on JVM, libgit2 on native, isomorphic-git on JS.
 */
interface GitOperations {
    /**
     * Clones a Git repository.
     */
    suspend fun clone(
        url: String,
        localPath: String,
        branch: String?,
        onProgress: (Float) -> Unit
    ): Boolean

    /**
     * Clones a Git repository with credentials.
     */
    suspend fun cloneWithCredentials(
        url: String,
        localPath: String,
        username: String,
        password: String,
        branch: String?,
        onProgress: (Float) -> Unit
    ): Boolean

    /**
     * Checks if a path is inside a Git repository.
     */
    suspend fun isGitRepository(path: String): Boolean

    /**
     * Gets the current branch name.
     */
    suspend fun getCurrentBranch(repoPath: String): String?

    /**
     * Gets the current commit hash.
     */
    suspend fun getCurrentCommitHash(repoPath: String): String?

    /**
     * Gets the remote URL.
     */
    suspend fun getRemoteUrl(repoPath: String): String?

    /**
     * Gets the last commit message.
     */
    suspend fun getLastCommitMessage(repoPath: String): String?

    /**
     * Gets the last commit author.
     */
    suspend fun getLastCommitAuthor(repoPath: String): String?

    /**
     * Gets the last commit date.
     */
    suspend fun getLastCommitDate(repoPath: String): Long?

    /**
     * Gets all changed files (tracked and untracked).
     */
    suspend fun getChangedFiles(repoPath: String): List<String>

    /**
     * Gets untracked files.
     */
    suspend fun getUntrackedFiles(repoPath: String): List<String>

    /**
     * Checks if there are uncommitted changes.
     */
    suspend fun hasUncommittedChanges(repoPath: String): Boolean

    /**
     * Fetches latest changes from remote.
     */
    suspend fun fetch(repoPath: String): Boolean

    /**
     * Pulls changes from remote.
     */
    suspend fun pull(repoPath: String): Boolean

    /**
     * Gets file history.
     */
    suspend fun getFileHistory(
        repoPath: String,
        filePath: String,
        maxCommits: Int = 50
    ): List<CommitInfo>

    /**
     * Gets file content at a specific commit.
     */
    suspend fun getFileAtCommit(repoPath: String, filePath: String, commitHash: String): String?

    /**
     * Gets all branches.
     */
    suspend fun getBranches(repoPath: String): List<BranchInfo>

    /**
     * Checks out a branch or commit.
     */
    suspend fun checkout(repoPath: String, branchOrCommit: String): Boolean

    /**
     * Creates a new branch.
     */
    suspend fun createBranch(repoPath: String, branchName: String): Boolean
}

/**
 * Information about a Git commit.
 */
data class CommitInfo(
    val hash: String,
    val shortHash: String,
    val message: String,
    val author: String,
    val email: String,
    val timestamp: Long
)

/**
 * Information about a Git branch.
 */
data class BranchInfo(
    val name: String,
    val fullName: String,
    val isCurrent: Boolean,
    val isRemote: Boolean,
    val commitHash: String
)
