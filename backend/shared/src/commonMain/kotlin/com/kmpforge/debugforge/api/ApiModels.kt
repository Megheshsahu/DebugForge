package com.kmpforge.debugforge.api

import kotlinx.serialization.Serializable

// Repository operations
@Serializable
data class LoadRepoRequest(val path: String)

@Serializable
data class CloneRepoRequest(val url: String, val localPath: String)

@Serializable
data class RepoInfo(
    val id: String,
    val path: String,
    val name: String,
    val gitInfo: GitInfo? = null,
    val modules: List<ModuleInfo> = emptyList(),
    val buildSystem: String = "gradle",
    val lastAnalyzed: Long? = null
)

@Serializable
data class GitInfo(
    val branch: String,
    val commitHash: String,
    val remoteUrl: String? = null,
    val isDirty: Boolean = false
)

@Serializable
data class ModuleInfo(
    val id: String,
    val name: String,
    val path: String,
    val type: String,
    val targets: List<String> = emptyList(),
    val dependencies: List<String> = emptyList()
)

@Serializable
data class LoadRepoResponse(
    val success: Boolean,
    val message: String,
    val repoId: String? = null,
    val error: String? = null,
    val repo: RepoInfo? = null
)

@Serializable
data class CloneRepoResponse(
    val success: Boolean,
    val message: String,
    val repoId: String? = null,
    val error: String? = null,
    val repo: RepoInfo? = null
)

// AI Analysis
@Serializable
data class AIAnalyzeRequest(
    val code: String,
    val fileName: String = "",
    val filePath: String = "",
    val context: String = ""
)

@Serializable
data class AIAnalyzeResponse(
    val success: Boolean,
    val analysis: String = "{}",
    val model: String = "",
    val error: String? = null
)

@Serializable
data class AISuggestion(
    val title: String,
    val rationale: String,
    val beforeCode: String = "",
    val afterCode: String = "",
    val confidence: Double = 0.0
)

@Serializable
data class AIAnalysisResult(
    val suggestions: List<AISuggestion> = emptyList(),
    val summary: String = ""
)