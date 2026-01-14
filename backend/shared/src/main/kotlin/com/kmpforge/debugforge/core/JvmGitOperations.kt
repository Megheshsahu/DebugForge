package com.kmpforge.debugforge.core

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * JVM implementation of GitOperations using JGit.
 */
class JvmGitOperations : GitOperations {
    override suspend fun clone(
        url: String,
        localPath: String,
        branch: String?,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        return try {
            // Validate and normalize the URL
            val normalizedUrl = normalizeGitUrl(url)
            
            val cloneCommand = Git.cloneRepository()
                .setURI(normalizedUrl)
                .setDirectory(File(localPath))

            if (branch != null) {
                cloneCommand.setBranch(branch)
            }

            cloneCommand.call()
            Result.success(Unit)
        } catch (e: Exception) {
            val enhancedError = enhanceGitError(e, url)
            Result.failure(enhancedError)
        }
    }

    override suspend fun cloneWithCredentials(
        url: String,
        localPath: String,
        username: String,
        password: String,
        branch: String?,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        return try {
            // Validate and normalize the URL
            val normalizedUrl = normalizeGitUrl(url)
            
            val cloneCommand = Git.cloneRepository()
                .setURI(normalizedUrl)
                .setDirectory(File(localPath))
                .setCredentialsProvider(UsernamePasswordCredentialsProvider(username, password))

            if (branch != null) {
                cloneCommand.setBranch(branch)
            }

            cloneCommand.call()
            Result.success(Unit)
        } catch (e: Exception) {
            val enhancedError = enhanceGitError(e, url)
            Result.failure(enhancedError)
        }
    }

    override suspend fun isGitRepository(path: String): Boolean {
        return try {
            FileRepositoryBuilder().setGitDir(File(path, ".git")).build() != null
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getCurrentBranch(repoPath: String): String? {
        return try {
            val repo = FileRepositoryBuilder().setGitDir(File(repoPath, ".git")).build()
            repo.branch
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getCurrentCommitHash(repoPath: String): String? {
        return try {
            val repo = FileRepositoryBuilder().setGitDir(File(repoPath, ".git")).build()
            val head = repo.resolve("HEAD")
            head?.name
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getRemoteUrl(repoPath: String): String? {
        return try {
            val repo = FileRepositoryBuilder().setGitDir(File(repoPath, ".git")).build()
            val config = repo.config
            config.getString("remote", "origin", "url")
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getLastCommitMessage(repoPath: String): String? {
        return try {
            val git = Git.open(File(repoPath))
            val log = git.log().setMaxCount(1).call()
            log.firstOrNull()?.fullMessage
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getLastCommitAuthor(repoPath: String): String? {
        return try {
            val git = Git.open(File(repoPath))
            val log = git.log().setMaxCount(1).call()
            log.firstOrNull()?.authorIdent?.name
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getLastCommitDate(repoPath: String): Long? {
        return try {
            val git = Git.open(File(repoPath))
            val log = git.log().setMaxCount(1).call()
            log.firstOrNull()?.commitTime?.toLong()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getChangedFiles(repoPath: String): List<String> {
        return try {
            val git = Git.open(File(repoPath))
            val status = git.status().call()
            (status.added + status.changed + status.modified + status.untracked).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getUntrackedFiles(repoPath: String): List<String> {
        return try {
            val git = Git.open(File(repoPath))
            val status = git.status().call()
            status.untracked.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun hasUncommittedChanges(repoPath: String): Boolean {
        return try {
            val git = Git.open(File(repoPath))
            val status = git.status().call()
            status.hasUncommittedChanges()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun fetch(repoPath: String): Boolean {
        return try {
            val git = Git.open(File(repoPath))
            git.fetch().call()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun pull(repoPath: String): Boolean {
        return try {
            val git = Git.open(File(repoPath))
            git.pull().call()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getFileHistory(
        repoPath: String,
        filePath: String,
        maxCommits: Int
    ): List<CommitInfo> {
        return try {
            val git = Git.open(File(repoPath))
            val log = git.log().addPath(filePath).setMaxCount(maxCommits).call()
            log.map { commit ->
                CommitInfo(
                    hash = commit.name,
                    shortHash = commit.name.substring(0, 7),
                    message = commit.shortMessage,
                    author = commit.authorIdent.name,
                    email = commit.authorIdent.emailAddress,
                    timestamp = commit.commitTime.toLong() * 1000
                )
            }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getFileAtCommit(repoPath: String, filePath: String, commitHash: String): String? {
        return try {
            val git = Git.open(File(repoPath))
            val tree = git.repository.resolve("$commitHash^{tree}")
            val treeWalk = org.eclipse.jgit.treewalk.TreeWalk(git.repository)
            treeWalk.addTree(tree)
            treeWalk.isRecursive = true
            treeWalk.filter = org.eclipse.jgit.treewalk.filter.PathFilter.create(filePath)

            if (treeWalk.next()) {
                val blobId = treeWalk.getObjectId(0)
                val loader = git.repository.open(blobId)
                String(loader.bytes)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getBranches(repoPath: String): List<BranchInfo> {
        return try {
            val git = Git.open(File(repoPath))
            val branches = git.branchList().call()
            val currentBranch = git.repository.branch

            branches.map { ref ->
                val name = ref.name.substringAfter("refs/heads/")
                BranchInfo(
                    name = name,
                    fullName = ref.name,
                    isCurrent = name == currentBranch,
                    isRemote = false,
                    commitHash = ref.objectId.name
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun checkout(repoPath: String, branchOrCommit: String): Boolean {
        return try {
            val git = Git.open(File(repoPath))
            git.checkout().setName(branchOrCommit).call()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun createBranch(repoPath: String, branchName: String): Boolean {
        return try {
            val git = Git.open(File(repoPath))
            git.branchCreate().setName(branchName).call()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun normalizeGitUrl(url: String): String {
        val trimmed = url.trim()
        
        // Handle GitHub URLs - extract owner/repo first
        if (trimmed.contains("github.com")) {
            val repoPath = when {
                trimmed.startsWith("https://github.com/") -> {
                    trimmed.removePrefix("https://github.com/").removeSuffix(".git")
                }
                trimmed.startsWith("git@github.com:") -> {
                    trimmed.removePrefix("git@github.com:").removeSuffix(".git")
                }
                else -> return trimmed
            }
            
            // Extract only the owner/repo part, ignoring additional path components
            val ownerRepoPart = repoPath.split("/").take(2).joinToString("/")
            
            // Reconstruct the clean Git URL
            val cleanUrl = when {
                trimmed.startsWith("https://github.com/") -> "https://github.com/$ownerRepoPart"
                trimmed.startsWith("git@github.com:") -> "git@github.com:$ownerRepoPart"
                else -> trimmed
            }
            
            // Add .git if not present
            return if (cleanUrl.endsWith(".git")) cleanUrl else "$cleanUrl.git"
        }
        
        // For other Git URLs, ensure they have .git extension if it's a GitHub-like URL
        if (trimmed.startsWith("https://") && trimmed.contains(".com/") && !trimmed.endsWith(".git")) {
            return "$trimmed.git"
        }
        
        return trimmed
    }
    
    private fun enhanceGitError(originalError: Exception, url: String): Exception {
        val message = originalError.message ?: "Unknown error"
        
        return when {
            message.contains("Invalid remote") || message.contains("Invalid remote: origin") -> {
                Exception("""
                    Invalid Git repository URL: ${originalError.message}
                    
                    This usually means:
                    • Repository URL is malformed or incorrect
                    • Repository doesn't exist
                    • Repository is private and requires authentication
                    • URL format is not supported
                    
                    Supported URL formats:
                    • https://github.com/user/repo (will be converted to https://github.com/user/repo.git)
                    • https://github.com/user/repo.git
                    • git@github.com:user/repo.git
                    
                    Please check:
                    1. Repository exists and is spelled correctly
                    2. You have permission to access it (public repos only for now)
                    3. URL format is correct
                    
                    Original URL: $url
                """.trimIndent(), originalError)
            }
            message.contains("Authentication failed") || message.contains("403") || message.contains("not authorized") -> {
                Exception("""
                    Authentication required: ${originalError.message}
                    
                    This repository appears to be private or requires authentication.
                    Currently, only public repositories are supported.
                    
                    For private repositories, you can:
                    1. Make the repository public temporarily
                    2. Clone it locally first, then load the local path
                    3. Use SSH authentication (future feature)
                    
                    Repository URL: $url
                """.trimIndent(), originalError)
            }
            message.contains("Repository not found") || message.contains("404") -> {
                Exception("""
                    Repository not found: ${originalError.message}
                    
                    Please verify:
                    • Repository exists on GitHub
                    • Repository name is spelled correctly
                    • Repository is not private (or you have access)
                    
                    You can check by visiting: ${url.replace(".git", "")}
                    
                    Repository URL: $url
                """.trimIndent(), originalError)
            }
            message.contains("getsockopt") || message.contains("Connection refused") -> {
                Exception("""
                    Network connection failed: ${originalError.message}
                    
                    This usually indicates:
                    • No internet connection
                    • Firewall blocking Git connections
                    • Corporate proxy blocking HTTPS traffic
                    • GitHub.com is unreachable
                    
                    Troubleshooting steps:
                    1. Check your internet connection
                    2. Try accessing https://github.com in a browser
                    3. Check firewall/proxy settings
                    4. Try using SSH instead of HTTPS: git@github.com:user/repo.git
                    5. Configure Git proxy if needed: git config --global http.proxy http://proxy.company.com:8080
                    
                    Repository URL: $url
                """.trimIndent(), originalError)
            }
            message.contains("timeout") || message.contains("Timeout") -> {
                Exception("""
                    Connection timeout: ${originalError.message}
                    
                    The connection to the Git server timed out. This could be due to:
                    • Slow internet connection
                    • Network congestion
                    • Server being busy
                    • Firewall/proxy timeouts
                    
                    Try again in a few minutes or check your network settings.
                    
                    Repository URL: $url
                """.trimIndent(), originalError)
            }
            else -> originalError
        }
    }
}