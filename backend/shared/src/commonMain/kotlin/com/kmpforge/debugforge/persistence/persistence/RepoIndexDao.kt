package com.kmpforge.debugforge.persistence

import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for repository indexing operations.
 * 
 * This interface abstracts SQLDelight database operations,
 * providing a clean API for the indexer, analyzers, and other components.
 */
interface RepoIndexDao {
    // ========================================================================
    // REPOSITORY METADATA
    // ========================================================================
    
    /**
     * Gets metadata for a repository.
     */
    suspend fun getRepoMetadata(repoPath: String): RepoMetadataEntity?
    
    /**
     * Inserts or updates repository metadata.
     */
    suspend fun upsertRepoMetadata(metadata: RepoMetadataEntity)
    
    /**
     * Updates the shared code percentage for a repository.
     */
    suspend fun updateSharedCodePercentage(repoPath: String, percentage: Float)
    
    /**
     * Deletes all data for a repository.
     */
    suspend fun deleteRepository(repoPath: String)
    
    /**
     * Clears all indexed data for a repository (alias for deleteRepository).
     */
    suspend fun clearRepository(repoPath: String) = deleteRepository(repoPath)
    
    // ========================================================================
    // MODULES
    // ========================================================================
    
    /**
     * Gets all modules in a repository.
     */
    suspend fun getModulesForRepo(repoPath: String): List<ModuleEntity>
    
    /**
     * Gets a specific module by its Gradle path.
     */
    suspend fun getModule(gradlePath: String): ModuleEntity?
    
    /**
     * Inserts or updates a module.
     */
    suspend fun upsertModule(module: ModuleEntity)
    
    /**
     * Gets all KMP modules in a repository.
     */
    suspend fun getKmpModules(repoPath: String): List<ModuleEntity>
    
    /**
     * Deletes all modules for a repository.
     */
    suspend fun deleteModulesForRepo(repoPath: String)
    
    // ========================================================================
    // SOURCE SETS
    // ========================================================================
    
    /**
     * Gets all source sets for a module.
     */
    suspend fun getSourceSetsForModule(moduleGradlePath: String): List<SourceSetEntity>
    
    /**
     * Inserts or updates a source set.
     */
    suspend fun upsertSourceSet(sourceSet: SourceSetEntity)
    
    /**
     * Gets source sets by platform for a module.
     */
    suspend fun getSourceSetsByPlatform(moduleGradlePath: String, platform: String): List<SourceSetEntity>
    
    // ========================================================================
    // FILES
    // ========================================================================
    
    /**
     * Gets a file by its absolute path.
     */
    suspend fun getFile(path: String): IndexedFileEntity?
    
    /**
     * Gets a file by its ID.
     */
    suspend fun getFileById(id: Long): IndexedFileEntity?
    
    /**
     * Gets all files in a module.
     */
    suspend fun getFilesInModule(moduleGradlePath: String): List<IndexedFileEntity>
    
    /**
     * Gets all files in a specific source set.
     */
    suspend fun getFilesInSourceSet(moduleGradlePath: String, sourceSet: String): List<IndexedFileEntity>
    
    /**
     * Gets all files in the specified source sets.
     */
    suspend fun getFilesInSourceSets(repoPath: String, sourceSets: List<String>): List<IndexedFileEntity>
    
    /**
     * Gets all files that have expect declarations.
     */
    suspend fun getFilesWithExpectDecls(): List<IndexedFileEntity>
    
    /**
     * Gets all files that have actual declarations.
     */
    suspend fun getFilesWithActualDecls(): List<IndexedFileEntity>
    
    /**
     * Inserts a new indexed file.
     */
    suspend fun insertFile(file: IndexedFileEntity): Long
    
    /**
     * Updates an existing file.
     */
    suspend fun updateFile(file: IndexedFileEntity)
    
    /**
     * Deletes a file by path.
     */
    suspend fun deleteFile(path: String)
    
    /**
     * Deletes files that are not in the given list.
     * Used for incremental re-indexing to clean up removed files.
     */
    suspend fun deleteFilesNotIn(moduleGradlePath: String, paths: List<String>)
    
    /**
     * Gets the file count for a module.
     */
    suspend fun getFileCount(moduleGradlePath: String): Long
    
    /**
     * Gets the total line count for a module.
     */
    suspend fun getTotalLineCount(moduleGradlePath: String): Long?
    
    /**
     * Gets the common code line count for a module.
     */
    suspend fun getCommonLineCount(moduleGradlePath: String): Long?
    
    // ========================================================================
    // SYMBOLS
    // ========================================================================
    
    /**
     * Gets a symbol by ID.
     */
    suspend fun getSymbol(id: Long): IndexedSymbolEntity?
    
    /**
     * Gets all symbols in a file.
     */
    suspend fun getSymbolsInFile(fileId: Long): List<IndexedSymbolEntity>
    
    /**
     * Gets a symbol by its fully qualified name.
     */
    suspend fun getSymbolByQualifiedName(qualifiedName: String): IndexedSymbolEntity?
    
    /**
     * Gets all symbols with a given name.
     */
    suspend fun getSymbolsByName(name: String): List<IndexedSymbolEntity>
    
    /**
     * Gets all expect symbols in the repository.
     */
    suspend fun getExpectSymbols(): List<ExpectSymbolWithFile>
    
    /**
     * Gets all actual symbols in the repository.
     */
    suspend fun getActualSymbols(): List<ActualSymbolWithFile>
    
    /**
     * Gets expect symbols in a specific file.
     */
    suspend fun getExpectSymbolsForFile(fileId: Long): List<IndexedSymbolEntity>
    
    /**
     * Gets actual symbols in a specific file.
     */
    suspend fun getActualSymbolsForFile(fileId: Long): List<IndexedSymbolEntity>
    
    /**
     * Gets suspend functions in a file.
     */
    suspend fun getSuspendFunctionsInFile(fileId: Long): List<IndexedSymbolEntity>
    
    /**
     * Inserts a new symbol.
     */
    suspend fun insertSymbol(symbol: IndexedSymbolEntity): Long
    
    /**
     * Deletes all symbols for a file.
     */
    suspend fun deleteSymbolsForFile(fileId: Long)
    
    /**
     * Gets all class/interface/object symbols.
     */
    suspend fun getClassSymbols(): List<IndexedSymbolEntity>
    
    /**
     * Gets top-level functions (no parent symbol).
     */
    suspend fun getTopLevelFunctions(): List<IndexedSymbolEntity>
    
    // ========================================================================
    // EXPECT/ACTUAL MAPPINGS
    // ========================================================================
    
    /**
     * Gets all expect/actual mappings.
     */
    suspend fun getExpectActualMappings(): List<ExpectActualMappingEntity>
    
    /**
     * Gets mappings for a specific expect symbol.
     */
    suspend fun getMappingsForExpect(expectSymbolId: Long): List<ExpectActualMappingEntity>
    
    /**
     * Gets all missing actual implementations.
     */
    suspend fun getMissingActuals(): List<MissingActualInfo>
    
    /**
     * Inserts or updates an expect/actual mapping.
     */
    suspend fun upsertExpectActualMapping(mapping: ExpectActualMappingEntity)
    
    /**
     * Deletes all mappings for an expect symbol.
     */
    suspend fun deleteExpectActualMappingsForSymbol(expectSymbolId: Long)
    
    // ========================================================================
    // REFERENCES
    // ========================================================================
    
    /**
     * Gets all references to a symbol.
     */
    suspend fun getReferencesToSymbol(symbolId: Long): List<SymbolReferenceWithFile>
    
    /**
     * Gets all references in a file.
     */
    suspend fun getReferencesInFile(fileId: Long): List<SymbolReferenceWithSymbol>
    
    /**
     * Inserts a symbol reference.
     */
    suspend fun insertReference(reference: SymbolReferenceEntity)
    
    /**
     * Deletes all references in a file.
     */
    suspend fun deleteReferencesInFile(fileId: Long)
    
    // ========================================================================
    // DIAGNOSTICS
    // ========================================================================
    
    /**
     * Gets diagnostics for a file.
     */
    suspend fun getDiagnosticsForFile(fileId: Long): List<PersistedDiagnosticEntity>
    
    /**
     * Gets all diagnostics as a flow for reactive updates.
     */
    fun getAllDiagnosticsFlow(): Flow<List<PersistedDiagnosticWithFile>>
    
    /**
     * Gets diagnostics by category.
     */
    suspend fun getDiagnosticsByCategory(category: String): List<PersistedDiagnosticWithFile>
    
    /**
     * Gets diagnostics by severity.
     */
    suspend fun getDiagnosticsBySeverity(severity: String): List<PersistedDiagnosticWithFile>
    
    /**
     * Inserts or updates a diagnostic.
     */
    suspend fun upsertDiagnostic(diagnostic: PersistedDiagnosticEntity)
    
    /**
     * Suppresses a diagnostic.
     */
    suspend fun suppressDiagnostic(diagnosticId: String, reason: String)
    
    /**
     * Deletes diagnostics for a file.
     */
    suspend fun deleteDiagnosticsForFile(fileId: Long)
    
    // ========================================================================
    // REFACTOR SUGGESTIONS
    // ========================================================================
    
    /**
     * Gets pending refactor suggestions.
     */
    suspend fun getPendingRefactors(): List<PersistedRefactorEntity>
    
    /**
     * Gets refactors by category.
     */
    suspend fun getRefactorsByCategory(category: String): List<PersistedRefactorEntity>
    
    /**
     * Inserts or updates a refactor suggestion.
     */
    suspend fun upsertRefactor(refactor: PersistedRefactorEntity)
    
    /**
     * Marks a refactor as applied.
     */
    suspend fun markRefactorApplied(refactorId: String)
    
    /**
     * Dismisses a refactor.
     */
    suspend fun dismissRefactor(refactorId: String, reason: String)
    
    // ========================================================================
    // SETTINGS
    // ========================================================================
    
    /**
     * Gets a setting value.
     */
    suspend fun getSetting(key: String): String?
    
    /**
     * Sets a setting value.
     */
    suspend fun setSetting(key: String, value: String)
    
    // ========================================================================
    // SHARING SNAPSHOTS
    // ========================================================================
    
    /**
     * Gets the latest sharing snapshot for a repository.
     */
    suspend fun getLatestSharingSnapshot(repoPath: String): SharingSnapshotEntity?
    
    /**
     * Gets sharing history for a repository.
     */
    suspend fun getSharingHistory(repoPath: String, limit: Long): List<SharingSnapshotEntity>
    
    /**
     * Inserts a sharing snapshot.
     */
    suspend fun insertSharingSnapshot(snapshot: SharingSnapshotEntity)
    
    // ========================================================================
    // TRANSACTIONS
    // ========================================================================
    
    /**
     * Executes multiple operations in a transaction.
     */
    suspend fun <T> transaction(block: suspend () -> T): T
}

// ============================================================================
// ENTITY CLASSES
// ============================================================================

data class RepoMetadataEntity(
    val repoPath: String,
    val gitRemoteUrl: String?,
    val gitBranch: String?,
    val gitCommitHash: String?,
    val lastIndexedAt: Long,
    val kotlinVersion: String?,
    val gradleVersion: String?,
    val moduleCount: Int,
    val fileCount: Int,
    val sharedCodePercentage: Float
)

data class ModuleEntity(
    val gradlePath: String,
    val repoPath: String,
    val displayName: String,
    val absolutePath: String,
    val buildFilePath: String?,
    val isKmpModule: Boolean,
    val hasCommonMain: Boolean,
    val moduleType: String,
    val indexedAt: Long
)

data class SourceSetEntity(
    val id: Long? = null,
    val moduleGradlePath: String,
    val name: String,
    val platform: String,
    val directoryPath: String,
    val fileCount: Int,
    val lineCount: Int
)

data class IndexedFileEntity(
    val id: Long? = null,
    val path: String,
    val relativePath: String,
    val moduleGradlePath: String,
    val sourceSet: String,
    val packageName: String?,
    val fileHash: String,
    val lineCount: Int,
    val lastIndexedAt: Long,
    val hasExpectDecls: Boolean,
    val hasActualDecls: Boolean
)

data class IndexedSymbolEntity(
    val id: Long? = null,
    val fileId: Long,
    val name: String,
    val qualifiedName: String,
    val kind: String,
    val visibility: String,
    val isExpect: Boolean,
    val isActual: Boolean,
    val isSuspend: Boolean,
    val isInline: Boolean,
    val isDataClass: Boolean,
    val isSealed: Boolean,
    val isCompanion: Boolean,
    val isExtension: Boolean,
    val startLine: Int,
    val endLine: Int,
    val startColumn: Int,
    val endColumn: Int,
    val signature: String?,
    val parentSymbolId: Long?,
    val annotations: String? // JSON array
)

data class ExpectSymbolWithFile(
    val symbol: IndexedSymbolEntity,
    val filePath: String,
    val sourceSet: String
)

data class ActualSymbolWithFile(
    val symbol: IndexedSymbolEntity,
    val filePath: String,
    val sourceSet: String
)

data class ExpectActualMappingEntity(
    val id: Long? = null,
    val expectSymbolId: Long,
    val actualSymbolId: Long?,
    val actualPlatform: String?,
    val isMissing: Boolean,
    val mismatchReason: String? // JSON
)

data class MissingActualInfo(
    val mapping: ExpectActualMappingEntity,
    val expectName: String,
    val expectQualifiedName: String,
    val expectFilePath: String
)

data class SymbolReferenceEntity(
    val id: Long? = null,
    val symbolId: Long,
    val referencingFileId: Long,
    val referenceLine: Int,
    val referenceColumn: Int,
    val referenceType: String
)

data class SymbolReferenceWithFile(
    val reference: SymbolReferenceEntity,
    val filePath: String
)

data class SymbolReferenceWithSymbol(
    val reference: SymbolReferenceEntity,
    val symbolName: String,
    val qualifiedName: String
)

data class PersistedDiagnosticEntity(
    val id: String,
    val fileId: Long,
    val severity: String,
    val category: String,
    val code: String,
    val title: String,
    val message: String,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val sourceAnalyzer: String,
    val suggestedFixJson: String?,
    val isSuppressed: Boolean,
    val suppressionReason: String?,
    val detectedAt: Long
)

data class PersistedDiagnosticWithFile(
    val diagnostic: PersistedDiagnosticEntity,
    val filePath: String
)

data class PersistedRefactorEntity(
    val id: String,
    val title: String,
    val rationale: String,
    val confidence: Float,
    val category: String,
    val priority: String,
    val unifiedDiff: String,
    val changesJson: String,
    val affectedFilesJson: String,
    val source: String,
    val isApplied: Boolean,
    val isDismissed: Boolean,
    val dismissalReason: String?,
    val generatedAt: Long
)

data class SharingSnapshotEntity(
    val id: Long? = null,
    val repoPath: String,
    val timestamp: Long,
    val totalFiles: Int,
    val commonFiles: Int,
    val platformFiles: Int,
    val totalLines: Int,
    val commonLines: Int,
    val sharedPercentage: Float,
    val perModuleJson: String
)

// Type aliases for compatibility
typealias IndexedFile = IndexedFileEntity
typealias IndexedSymbol = IndexedSymbolEntity
typealias SymbolReference = SymbolReferenceEntity
typealias ExpectActualMapping = ExpectActualMappingEntity
