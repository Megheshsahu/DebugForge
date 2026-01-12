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
    ): Boolean {
        return try {
            val cloneCommand = Git.cloneRepository()
                .setURI(url)
                .setDirectory(File(localPath))

            if (branch != null) {
                cloneCommand.setBranch(branch)
            }

            cloneCommand.call()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun cloneWithCredentials(
        url: String,
        localPath: String,
        username: String,
        password: String,
        branch: String?,
        onProgress: (Float) -> Unit
    ): Boolean {
        return try {
            val cloneCommand = Git.cloneRepository()
                .setURI(url)
                .setDirectory(File(localPath))
                .setCredentialsProvider(UsernamePasswordCredentialsProvider(username, password))

            if (branch != null) {
                cloneCommand.setBranch(branch)
            }

            cloneCommand.call()
            true
        } catch (e: Exception) {
            false
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
}