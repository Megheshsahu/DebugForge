package com.kmpforge.debugforge.api

import kotlinx.serialization.Serializable

// Repository operations
@Serializable
data class LoadRepoRequest(val path: String)

@Serializable
data class CloneRepoRequest(val url: String, val localPath: String)

@Serializable
data class LoadRepoResponse(
    val success: Boolean,
    val message: String,
    val repoId: String? = null,
    val error: String? = null
)

@Serializable
data class CloneRepoResponse(
    val success: Boolean,
    val message: String,
    val repoId: String? = null,
    val error: String? = null
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