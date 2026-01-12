package com.kmpforge.debugforge.sync

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GitHubService(
    private val token: String,
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "https://api.github.com"

    suspend fun getFileContent(
        owner: String,
        repo: String,
        path: String,
        branch: String = "main"
    ): GitHubFileResult {
        return try {
            val response = httpClient.get("$baseUrl/repos/$owner/$repo/contents/$path") {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github.v3+json")
                parameter("ref", branch)
            }
            if (response.status.isSuccess()) {
                val content = json.decodeFromString<GitHubContent>(response.bodyAsText())
                GitHubFileResult.Success(
                    content = content.content.decodeBase64(),
                    sha = content.sha
                )
            } else {
                GitHubFileResult.Failed("Failed to get file: ${response.status}")
            }
        } catch (e: Exception) {
            GitHubFileResult.Failed("Error: ${e.message}")
        }
    }

    suspend fun updateFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        sha: String,
        branch: String = "main"
    ): GitHubUpdateResult {
        return try {
            val encodedContent = content.encodeToBase64()
            val response = httpClient.put("$baseUrl/repos/$owner/$repo/contents/$path") {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github.v3+json")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(
                    GitHubUpdateRequest.serializer(),
                    GitHubUpdateRequest(
                        message = message,
                        content = encodedContent,
                        sha = sha,
                        branch = branch
                    )
                ))
            }
            if (response.status.isSuccess()) {
                val result = json.decodeFromString<GitHubUpdateResponse>(response.bodyAsText())
                GitHubUpdateResult.Success(
                    commitSha = result.commit.sha,
                    commitUrl = result.commit.htmlUrl
                )
            } else {
                GitHubUpdateResult.Failed("Failed to update file: ${response.status}")
            }
        } catch (e: Exception) {
            GitHubUpdateResult.Failed("Error: ${e.message}")
        }
    }

    suspend fun createBranch(
        owner: String,
        repo: String,
        branchName: String,
        fromBranch: String = "main"
    ): BranchResult {
        return try {
            val refResponse = httpClient.get("$baseUrl/repos/$owner/$repo/git/refs/heads/$fromBranch") {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github.v3+json")
            }
            val refData = json.decodeFromString<GitHubRef>(refResponse.bodyAsText())
            val baseSha = refData.`object`.sha
            val createResponse = httpClient.post("$baseUrl/repos/$owner/$repo/git/refs") {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github.v3+json")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(
                    CreateBranchRequest.serializer(),
                    CreateBranchRequest(
                        ref = "refs/heads/$branchName",
                        sha = baseSha
                    )
                ))
            }
            if (createResponse.status.isSuccess()) {
                BranchResult.Success(branchName)
            } else {
                BranchResult.Failed("Failed to create branch: ${createResponse.status}")
            }
        } catch (e: Exception) {
            BranchResult.Failed("Error: ${e.message}")
        }
    }

    suspend fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        body: String,
        headBranch: String,
        baseBranch: String = "main"
    ): PullRequestResult {
        return try {
            val response = httpClient.post("$baseUrl/repos/$owner/$repo/pulls") {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github.v3+json")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(
                    CreatePullRequestRequest.serializer(),
                    CreatePullRequestRequest(
                        title = title,
                        body = body,
                        head = headBranch,
                        base = baseBranch
                    )
                ))
            }
            if (response.status.isSuccess()) {
                val pr = json.decodeFromString<PullRequest>(response.bodyAsText())
                PullRequestResult.Success(
                    number = pr.number,
                    url = pr.htmlUrl
                )
            } else {
                PullRequestResult.Failed("Failed to create PR: ${response.status}")
            }
        } catch (e: Exception) {
            PullRequestResult.Failed("Error: ${e.message}")
        }
    }

    suspend fun listFiles(
        owner: String,
        repo: String,
        path: String = "",
        branch: String = "main"
    ): ListFilesResult {
        return try {
            val response = httpClient.get("$baseUrl/repos/$owner/$repo/contents/$path") {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github.v3+json")
                parameter("ref", branch)
            }
            if (response.status.isSuccess()) {
                val files = json.decodeFromString<List<GitHubContent>>(response.bodyAsText())
                ListFilesResult.Success(files.map {
                    FileInfo(
                        name = it.name,
                        path = it.path,
                        type = it.type,
                        sha = it.sha
                    )
                })
            } else {
                ListFilesResult.Failed("Failed to list files: ${response.status}")
            }
        } catch (e: Exception) {
            ListFilesResult.Failed("Error: ${e.message}")
        }
    }

    private fun String.encodeToBase64(): String {
        return this.encodeToByteArray().encodeBase64()
    }

    private fun ByteArray.encodeBase64(): String {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val output = StringBuilder()
        var padding = 0
        var position = 0
        while (position < this.size) {
            var b = this[position].toInt() and 0xFF shl 16 and 0xFFFFFF
            if (position + 1 < this.size) {
                b = b or (this[position + 1].toInt() and 0xFF shl 8)
            } else {
                padding++
            }
            if (position + 2 < this.size) {
                b = b or (this[position + 2].toInt() and 0xFF)
            } else {
                padding++
            }
            for (i in 0 until 4 - padding) {
                val c = b and 0xFC0000 shr 18
                output.append(table[c])
                b = b shl 6
            }
            position += 3
        }
        for (i in 0 until padding) {
            output.append("=")
        }
        return output.toString()
    }

    private fun String.decodeBase64(): String {
        val cleaned = this.replace("\n", "").replace("\r", "")
        val bytes = mutableListOf<Byte>()
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        var i = 0
        while (i < cleaned.length) {
            val b1 = table.indexOf(cleaned[i])
            val b2 = table.indexOf(cleaned[i + 1])
            val b3 = if (i + 2 < cleaned.length && cleaned[i + 2] != '=') table.indexOf(cleaned[i + 2]) else 0
            val b4 = if (i + 3 < cleaned.length && cleaned[i + 3] != '=') table.indexOf(cleaned[i + 3]) else 0
            bytes.add((b1 shl 2 or (b2 shr 4)).toByte())
            if (i + 2 < cleaned.length && cleaned[i + 2] != '=') {
                bytes.add((b2 shl 4 or (b3 shr 2)).toByte())
            }
            if (i + 3 < cleaned.length && cleaned[i + 3] != '=') {
                bytes.add((b3 shl 6 or b4).toByte())
            }
            i += 4
        }
        return bytes.toByteArray().decodeToString()
    }
}

@Serializable
private data class GitHubContent(
    val name: String,
    val path: String,
    val sha: String,
    val type: String,
    val content: String = ""
)

@Serializable
private data class GitHubUpdateRequest(
    val message: String,
    val content: String,
    val sha: String,
    val branch: String
)

@Serializable
private data class GitHubUpdateResponse(
    val commit: CommitInfo
)

@Serializable
private data class CommitInfo(
    val sha: String,
    val htmlUrl: String
)

@Serializable
private data class GitHubRef(
    val `object`: GitObject
)

@Serializable
private data class GitObject(
    val sha: String
)

@Serializable
private data class CreateBranchRequest(
    val ref: String,
    val sha: String
)

@Serializable
private data class CreatePullRequestRequest(
    val title: String,
    val body: String,
    val head: String,
    val base: String
)

@Serializable
private data class PullRequest(
    val number: Int,
    val htmlUrl: String
)

sealed class GitHubFileResult {
    data class Success(val content: String, val sha: String) : GitHubFileResult()
    data class Failed(val error: String) : GitHubFileResult()
}

sealed class GitHubUpdateResult {
    data class Success(val commitSha: String, val commitUrl: String) : GitHubUpdateResult()
    data class Failed(val error: String) : GitHubUpdateResult()
}

sealed class BranchResult {
    data class Success(val branchName: String) : BranchResult()
    data class Failed(val error: String) : BranchResult()
}

sealed class PullRequestResult {
    data class Success(val number: Int, val url: String) : PullRequestResult()
    data class Failed(val error: String) : PullRequestResult()
}

sealed class ListFilesResult {
    data class Success(val files: List<FileInfo>) : ListFilesResult()
    data class Failed(val error: String) : ListFilesResult()
}

data class FileInfo(
    val name: String,
    val path: String,
    val type: String,
    val sha: String
)
