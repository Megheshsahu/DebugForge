package com.kmpforge.debugforge.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Indexes source code into the SQLDelight database for fast querying.
 * Supports incremental re-indexing based on file content hashes.
 */
interface ProjectIndexer {
    /**
     * Current indexing state.
     */
    val indexingState: StateFlow<IndexingState>
    
    /**
     * Indexes a parsed repository, storing all symbols and metadata.
     * Uses incremental indexing when possible.
     * 
     * @param repository Parsed repository to index
     * @param forceFullReindex If true, ignores hashes and re-indexes everything
     * @return Indexing result with statistics
     */
    suspend fun index(repository: ParsedRepository, forceFullReindex: Boolean = false): IndexingResult
    
    /**
     * Indexes only changed files based on hashes.
     * 
     * @param repository Repository to check for changes
     * @return Delta result showing what was re-indexed
     */
    suspend fun indexDelta(repository: ParsedRepository): IndexingDeltaResult
    
    /**
     * Clears all indexed data for a repository.
     */
    suspend fun clearIndex(repoPath: String)
    
    /**
     * Gets indexing statistics for a repository.
     */
    suspend fun getStats(repoPath: String): IndexingStats?
}

/**
 * State of the indexing process.
 */
sealed class IndexingState {
    data object Idle : IndexingState()
    data class InProgress(
        val phase: IndexingPhase,
        val progress: Float,
        val currentFile: String?,
        val filesProcessed: Int,
        val totalFiles: Int
    ) : IndexingState()
    data class Completed(val stats: IndexingStats) : IndexingState()
    data class Failed(val error: String) : IndexingState()
}

enum class IndexingPhase {
    PARSING,
    EXTRACTING_SYMBOLS,
    RESOLVING_REFERENCES,
    MAPPING_EXPECT_ACTUAL,
    STORING,
    FINALIZING
}

/**
 * Result of a full indexing operation.
 */
data class IndexingResult(
    val success: Boolean,
    val stats: IndexingStats,
    val errors: List<IndexingError>
)

/**
 * Result of an incremental indexing operation.
 */
data class IndexingDeltaResult(
    val filesAdded: Int,
    val filesModified: Int,
    val filesRemoved: Int,
    val symbolsAdded: Int,
    val symbolsModified: Int,
    val symbolsRemoved: Int,
    val durationMs: Long
)

/**
 * Indexing statistics.
 */
data class IndexingStats(
    val totalFiles: Int,
    val totalSymbols: Int,
    val totalExpectDeclarations: Int,
    val totalActualImplementations: Int,
    val totalReferences: Int,
    val indexedAt: Long,
    val durationMs: Long
)

/**
 * Error during indexing.
 */
data class IndexingError(
    val filePath: String,
    val line: Int?,
    val message: String,
    val isFatal: Boolean
)
