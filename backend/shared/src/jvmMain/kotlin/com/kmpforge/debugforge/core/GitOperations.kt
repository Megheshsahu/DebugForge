package com.kmpforge.debugforge.core

import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.treewalk.TreeWalk

actual object GitOperations {

    actual suspend fun clone(
        url: String,
        localPath: String,
        branch: String?,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        return try {
            val cloneCommand = Git.cloneRepository()
                .setURI(url)
                .setDirectory(File(localPath))

            if (branch != null) {
                cloneCommand.setBranch(branch)
            }

            cloneCommand.call()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    actual suspend fun cloneWithCredentials(
        url: String,
        localPath: String,
        username: String,
        password: String,
        branch: String?,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        return try {
            val cloneCommand = Git.cloneRepository()
                .setURI(url)
                .setDirectory(File(localPath))
                .setCredentialsProvider(org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider(username, password))

            if (branch != null) {
                cloneCommand.setBranch(branch)
            }

            cloneCommand.call()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    actual suspend fun isGitRepository(path: String): Boolean {
        return try {
            FileRepositoryBuilder().setGitDir(File(path, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()
            true
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun getCurrentBranch(repoPath: String): String? {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()
            repository.branch
        } catch (e: Exception) {
            null
        }
    }

    actual suspend fun getCurrentCommitHash(repoPath: String): String? {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            val head = repository.resolve("HEAD")
            head?.name
        } catch (e: Exception) {
            null
        }
    }

    actual suspend fun getRemoteUrl(repoPath: String): String? {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            val config = repository.config
            config.getString("remote", "origin", "url")
        } catch (e: Exception) {
            null
        }
    }

    actual suspend fun getLastCommitMessage(repoPath: String): String? {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            val git = Git(repository)
            val commits = git.log().setMaxCount(1).call()
            commits.firstOrNull()?.shortMessage
        } catch (e: Exception) {
            null
        }
    }

    actual suspend fun getLastCommitAuthor(repoPath: String): String? {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            val git = Git(repository)
            val commits = git.log().setMaxCount(1).call()
            commits.firstOrNull()?.authorIdent?.name
        } catch (e: Exception) {
            null
        }
    }

    actual suspend fun getLastCommitDate(repoPath: String): Long? {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            val git = Git(repository)
            val commits = git.log().setMaxCount(1).call()
            commits.firstOrNull()?.commitTime?.toLong()?.times(1000)
        } catch (e: Exception) {
            null
        }
    }

    actual suspend fun getChangedFiles(repoPath: String): List<String> {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            val git = Git(repository)
            val status = git.status().call()
            status.added.toList() + status.changed.toList() + status.modified.toList() + status.removed.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    actual suspend fun getUntrackedFiles(repoPath: String): List<String> {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            val git = Git(repository)
            val status = git.status().call()
            status.untracked.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    actual suspend fun hasUncommittedChanges(repoPath: String): Boolean {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            val git = Git(repository)
            val status = git.status().call()
            !status.isClean
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun fetch(repoPath: String): Boolean {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            val git = Git(repository)
            git.fetch().call()
            true
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun pull(repoPath: String): Boolean {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            val git = Git(repository)
            git.pull().call()
            true
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun getFileHistory(
        repoPath: String,
        filePath: String,
        maxCommits: Int
    ): List<CommitInfo> {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            val git = Git(repository)
            val commits = git.log().addPath(filePath).setMaxCount(maxCommits).call()

            commits.map { commit ->
                CommitInfo(
                    hash = commit.name,
                    shortHash = commit.name.substring(0, 7),
                    message = commit.shortMessage,
                    author = commit.authorIdent.name,
                    date = commit.commitTime.toLong() * 1000
                )
            }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    actual suspend fun getFileAtCommit(
        repoPath: String,
        filePath: String,
        commitHash: String
    ): String? {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            val git = Git(repository)
            val revWalk = RevWalk(repository)
            val commit = revWalk.parseCommit(ObjectId.fromString(commitHash))
            val tree = commit.tree

            val treeWalk = TreeWalk(repository)
            treeWalk.addTree(tree)
            treeWalk.isRecursive = true

            while (treeWalk.next()) {
                if (treeWalk.pathString == filePath) {
                    val objectLoader = repository.open(treeWalk.getObjectId(0))
                    return String(objectLoader.bytes)
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    actual suspend fun getBranches(repoPath: String): List<BranchInfo> {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            val git = Git(repository)
            val branches = git.branchList().call()

            branches.map { ref ->
                val name = ref.name.substringAfter("refs/heads/")
                BranchInfo(
                    name = name,
                    fullName = ref.name,
                    isCurrent = ref.name == "refs/heads/${repository.branch}"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    actual suspend fun checkout(repoPath: String, branchOrCommit: String): Boolean {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            val git = Git(repository)
            git.checkout().setName(branchOrCommit).call()
            true
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun createBranch(repoPath: String, branchName: String): Boolean {
        return try {
            val repository = FileRepositoryBuilder().setGitDir(File(repoPath, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()

            val git = Git(repository)
            git.branchCreate().setName(branchName).call()
            true
        } catch (e: Exception) {
            false
        }
    }
}
