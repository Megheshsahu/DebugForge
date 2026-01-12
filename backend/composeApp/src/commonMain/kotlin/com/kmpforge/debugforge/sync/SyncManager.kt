package com.kmpforge.debugforge.sync

import com.kmpforge.debugforge.sync.GitHubService

sealed class SyncResult {
	data class Success(val prNumber: Int, val prUrl: String, val branch: String) : SyncResult()
	data class Failed(val error: String) : SyncResult()
}

class SyncManager(private val githubService: GitHubService) {
	suspend fun applyAndSync(
		owner: String,
		repo: String,
		filePath: String,
		newContent: String,
		fixDescription: String
	): SyncResult {
		// Minimal stub: always fail (replace with real logic as needed)
		return SyncResult.Failed("SyncManager logic not implemented.")
	}
}
