package com.kmpforge.debugforge.core

/**
 * Platform-agnostic Git operations abstraction.
 * Implemented differently on JVM, Native, and JS platforms.
 */
expect object GitOperations {
    /**
     * Clones a Git repository to the specified local path.
     */
    suspend fun clone(
        url: String,
        localPath: String,
        branch: String?,
        onProgress: (Float) -> Unit
    ): Result<Unit>

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
    ): Result<Unit>

    /**
     * Checks if the given path is a Git repository.
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
     * Gets the list of changed files.
     */
    suspend fun getChangedFiles(repoPath: String): List<String>

    /**
     * Gets the list of untracked files.
     */
    suspend fun getUntrackedFiles(repoPath: String): List<String>

    /**
     * Checks if there are uncommitted changes.
     */
    suspend fun hasUncommittedChanges(repoPath: String): Boolean

    /**
     * Fetches from remote.
     */
    suspend fun fetch(repoPath: String): Boolean

    /**
     * Pulls changes from remote.
     */
    suspend fun pull(repoPath: String): Boolean

    /**
     * Gets the file history.
     */
    suspend fun getFileHistory(
        repoPath: String,
        filePath: String,
        maxCommits: Int = 10
    ): List<CommitInfo>

    /**
     * Gets the file content at a specific commit.
     */
    suspend fun getFileAtCommit(
        repoPath: String,
        filePath: String,
        commitHash: String
    ): String?

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
 * Represents a Git commit.
 */
data class CommitInfo(
    val hash: String,
    val shortHash: String,
    val message: String,
    val author: String,
    val date: Long
)

/**
 * Represents a Git branch.
 */
data class BranchInfo(
    val name: String,
    val fullName: String,
    val isCurrent: Boolean
)
