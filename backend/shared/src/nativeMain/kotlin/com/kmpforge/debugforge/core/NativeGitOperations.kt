package com.kmpforge.debugforge.core

/**
 * Native implementation of GitOperations using libgit2.
 * TODO: Implement full functionality
 */
actual class NativeGitOperations : GitOperations {
    actual override suspend fun clone(url: String, localPath: String, branch: String?, onProgress: (Float) -> Unit): Result<Unit> = TODO("Not yet implemented")
    actual override suspend fun cloneWithCredentials(url: String, localPath: String, username: String, password: String, branch: String?, onProgress: (Float) -> Unit): Result<Unit> = TODO("Not yet implemented")
    actual override suspend fun isGitRepository(path: String): Boolean = TODO("Not yet implemented")
    actual override suspend fun getCurrentBranch(repoPath: String): String? = TODO("Not yet implemented")
    actual override suspend fun getCurrentCommitHash(repoPath: String): String? = TODO("Not yet implemented")
    actual override suspend fun getRemoteUrl(repoPath: String): String? = TODO("Not yet implemented")
    actual override suspend fun getLastCommitMessage(repoPath: String): String? = TODO("Not yet implemented")
    actual override suspend fun getLastCommitAuthor(repoPath: String): String? = TODO("Not yet implemented")
    actual override suspend fun getLastCommitDate(repoPath: String): Long? = TODO("Not yet implemented")
    actual override suspend fun getChangedFiles(repoPath: String): List<String> = TODO("Not yet implemented")
    actual override suspend fun getUntrackedFiles(repoPath: String): List<String> = TODO("Not yet implemented")
    actual override suspend fun hasUncommittedChanges(repoPath: String): Boolean = TODO("Not yet implemented")
    actual override suspend fun fetch(repoPath: String): Boolean = TODO("Not yet implemented")
    actual override suspend fun pull(repoPath: String): Boolean = TODO("Not yet implemented")
    actual override suspend fun getFileHistory(repoPath: String, filePath: String, maxCommits: Int): List<CommitInfo> = TODO("Not yet implemented")
    actual override suspend fun getFileAtCommit(repoPath: String, filePath: String, commitHash: String): String? = TODO("Not yet implemented")
    actual override suspend fun getBranches(repoPath: String): List<BranchInfo> = TODO("Not yet implemented")
    actual override suspend fun checkout(repoPath: String, branchOrCommit: String): Boolean = TODO("Not yet implemented")
    actual override suspend fun createBranch(repoPath: String, branchName: String): Boolean = TODO("Not yet implemented")
}