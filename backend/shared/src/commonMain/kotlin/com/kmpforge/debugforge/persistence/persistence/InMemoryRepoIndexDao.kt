package com.kmpforge.debugforge.persistence

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Kotlin common compatible removeIf for MutableMap entries
 */
private inline fun <K, V> MutableMap<K, V>.removeEntriesIf(predicate: (Map.Entry<K, V>) -> Boolean) {
    val iterator = this.entries.iterator()
    while (iterator.hasNext()) {
        if (predicate(iterator.next())) {
            iterator.remove()
        }
    }
}

/**
 * Kotlin common compatible removeIf for MutableList
 */
private inline fun <T> MutableList<T>.removeItemsIf(predicate: (T) -> Boolean) {
    val iterator = this.iterator()
    while (iterator.hasNext()) {
        if (predicate(iterator.next())) {
            iterator.remove()
        }
    }
}

/**
 * In-memory implementation of RepoIndexDao for initial development.
 * This implementation stores all data in memory and is suitable for testing
 * and development purposes. For production, use the SQLDelight-backed implementation.
 */
class InMemoryRepoIndexDao : RepoIndexDao {
    
    private val repoMetadata = mutableMapOf<String, RepoMetadataEntity>()
    private val modules = mutableMapOf<String, ModuleEntity>()
    private val sourceSets = mutableMapOf<Long, SourceSetEntity>()
    private val files = mutableMapOf<String, IndexedFileEntity>()
    private val filesByIdMap = mutableMapOf<Long, IndexedFileEntity>()
    private val symbols = mutableMapOf<Long, IndexedSymbolEntity>()
    private val mappings = mutableMapOf<Long, ExpectActualMappingEntity>()
    private val references = mutableListOf<SymbolReferenceEntity>()
    private val diagnostics = mutableMapOf<String, PersistedDiagnosticEntity>()
    private val refactors = mutableMapOf<String, PersistedRefactorEntity>()
    private val settings = mutableMapOf<String, String>()
    private val snapshots = mutableListOf<SharingSnapshotEntity>()
    
    private val diagnosticsFlow = MutableStateFlow<List<PersistedDiagnosticWithFile>>(emptyList())
    
    private var nextFileId = 1L
    private var nextSymbolId = 1L
    private var nextSourceSetId = 1L
    private var nextMappingId = 1L
    private var nextReferenceId = 1L
    private var nextSnapshotId = 1L
    
    // ========================================================================
    // REPOSITORY METADATA
    // ========================================================================
    
    override suspend fun getRepoMetadata(repoPath: String): RepoMetadataEntity? = 
        repoMetadata[repoPath]
    
    override suspend fun upsertRepoMetadata(metadata: RepoMetadataEntity) {
        repoMetadata[metadata.repoPath] = metadata
    }
    
    override suspend fun updateSharedCodePercentage(repoPath: String, percentage: Float) {
        repoMetadata[repoPath]?.let {
            repoMetadata[repoPath] = it.copy(sharedCodePercentage = percentage)
        }
    }
    
    override suspend fun deleteRepository(repoPath: String) {
        repoMetadata.remove(repoPath)
        modules.removeEntriesIf { it.value.repoPath == repoPath }
        // Would need to cascade delete other entities
    }
    
    // ========================================================================
    // MODULES
    // ========================================================================
    
    override suspend fun getModulesForRepo(repoPath: String): List<ModuleEntity> =
        modules.values.filter { it.repoPath == repoPath }
    
    override suspend fun getModule(gradlePath: String): ModuleEntity? = modules[gradlePath]
    
    override suspend fun upsertModule(module: ModuleEntity) {
        modules[module.gradlePath] = module
    }
    
    override suspend fun getKmpModules(repoPath: String): List<ModuleEntity> =
        modules.values.filter { it.repoPath == repoPath && it.isKmpModule }
    
    override suspend fun deleteModulesForRepo(repoPath: String) {
        modules.removeEntriesIf { it.value.repoPath == repoPath }
    }
    
    // ========================================================================
    // SOURCE SETS
    // ========================================================================
    
    override suspend fun getSourceSetsForModule(moduleGradlePath: String): List<SourceSetEntity> =
        sourceSets.values.filter { it.moduleGradlePath == moduleGradlePath }
    
    override suspend fun upsertSourceSet(sourceSet: SourceSetEntity) {
        val id = sourceSet.id ?: nextSourceSetId++
        sourceSets[id] = sourceSet.copy(id = id)
    }
    
    override suspend fun getSourceSetsByPlatform(moduleGradlePath: String, platform: String): List<SourceSetEntity> =
        sourceSets.values.filter { it.moduleGradlePath == moduleGradlePath && it.platform == platform }
    
    // ========================================================================
    // FILES
    // ========================================================================
    
    override suspend fun getFile(path: String): IndexedFileEntity? = files[path]
    
    override suspend fun getFileById(id: Long): IndexedFileEntity? = filesByIdMap[id]
    
    override suspend fun getFilesInModule(moduleGradlePath: String): List<IndexedFileEntity> =
        files.values.filter { it.moduleGradlePath == moduleGradlePath }
    
    override suspend fun getFilesInSourceSet(moduleGradlePath: String, sourceSet: String): List<IndexedFileEntity> =
        files.values.filter { it.moduleGradlePath == moduleGradlePath && it.sourceSet == sourceSet }
    
    override suspend fun getFilesInSourceSets(repoPath: String, sourceSets: List<String>): List<IndexedFileEntity> =
        files.values.filter { it.sourceSet in sourceSets }
    
    override suspend fun getFilesWithExpectDecls(): List<IndexedFileEntity> =
        files.values.filter { it.hasExpectDecls }
    
    override suspend fun getFilesWithActualDecls(): List<IndexedFileEntity> =
        files.values.filter { it.hasActualDecls }
    
    override suspend fun insertFile(file: IndexedFileEntity): Long {
        val id = file.id ?: nextFileId++
        val fileWithId = file.copy(id = id)
        files[file.path] = fileWithId
        filesByIdMap[id] = fileWithId
        return id
    }
    
    override suspend fun updateFile(file: IndexedFileEntity) {
        file.id?.let { id ->
            files[file.path] = file
            filesByIdMap[id] = file
        }
    }
    
    override suspend fun deleteFile(path: String) {
        files.remove(path)?.id?.let { filesByIdMap.remove(it) }
    }
    
    override suspend fun deleteFilesNotIn(moduleGradlePath: String, paths: List<String>) {
        files.removeEntriesIf { it.value.moduleGradlePath == moduleGradlePath && it.key !in paths }
    }
    
    override suspend fun getFileCount(moduleGradlePath: String): Long =
        files.values.count { it.moduleGradlePath == moduleGradlePath }.toLong()
    
    override suspend fun getTotalLineCount(moduleGradlePath: String): Long? =
        files.values.filter { it.moduleGradlePath == moduleGradlePath }
            .sumOf { it.lineCount }.toLong()
    
    override suspend fun getCommonLineCount(moduleGradlePath: String): Long? =
        files.values.filter { it.moduleGradlePath == moduleGradlePath && it.sourceSet.contains("common", true) }
            .sumOf { it.lineCount }.toLong()
    
    // ========================================================================
    // SYMBOLS
    // ========================================================================
    
    override suspend fun getSymbol(id: Long): IndexedSymbolEntity? = symbols[id]
    
    override suspend fun getSymbolsInFile(fileId: Long): List<IndexedSymbolEntity> =
        symbols.values.filter { it.fileId == fileId }
    
    override suspend fun getSymbolByQualifiedName(qualifiedName: String): IndexedSymbolEntity? =
        symbols.values.find { it.qualifiedName == qualifiedName }
    
    override suspend fun getSymbolsByName(name: String): List<IndexedSymbolEntity> =
        symbols.values.filter { it.name == name }
    
    override suspend fun getExpectSymbols(): List<ExpectSymbolWithFile> =
        symbols.values.filter { it.isExpect }.map { symbol ->
            val file = filesByIdMap[symbol.fileId]
            ExpectSymbolWithFile(symbol, file?.path ?: "", file?.sourceSet ?: "")
        }
    
    override suspend fun getActualSymbols(): List<ActualSymbolWithFile> =
        symbols.values.filter { it.isActual }.map { symbol ->
            val file = filesByIdMap[symbol.fileId]
            ActualSymbolWithFile(symbol, file?.path ?: "", file?.sourceSet ?: "")
        }
    
    override suspend fun getExpectSymbolsForFile(fileId: Long): List<IndexedSymbolEntity> =
        symbols.values.filter { it.fileId == fileId && it.isExpect }
    
    override suspend fun getActualSymbolsForFile(fileId: Long): List<IndexedSymbolEntity> =
        symbols.values.filter { it.fileId == fileId && it.isActual }
    
    override suspend fun getSuspendFunctionsInFile(fileId: Long): List<IndexedSymbolEntity> =
        symbols.values.filter { it.fileId == fileId && it.isSuspend }
    
    override suspend fun insertSymbol(symbol: IndexedSymbolEntity): Long {
        val id = symbol.id ?: nextSymbolId++
        symbols[id] = symbol.copy(id = id)
        return id
    }
    
    override suspend fun deleteSymbolsForFile(fileId: Long) {
        symbols.removeEntriesIf { it.value.fileId == fileId }
    }
    
    override suspend fun getClassSymbols(): List<IndexedSymbolEntity> =
        symbols.values.filter { it.kind in listOf("CLASS", "INTERFACE", "OBJECT") }
    
    override suspend fun getTopLevelFunctions(): List<IndexedSymbolEntity> =
        symbols.values.filter { it.kind == "FUNCTION" && it.parentSymbolId == null }
    
    // ========================================================================
    // EXPECT/ACTUAL MAPPINGS
    // ========================================================================
    
    override suspend fun getExpectActualMappings(): List<ExpectActualMappingEntity> =
        mappings.values.toList()
    
    override suspend fun getMappingsForExpect(expectSymbolId: Long): List<ExpectActualMappingEntity> =
        mappings.values.filter { it.expectSymbolId == expectSymbolId }
    
    override suspend fun getMissingActuals(): List<MissingActualInfo> =
        mappings.values.filter { it.isMissing }.mapNotNull { mapping ->
            val expect = symbols[mapping.expectSymbolId] ?: return@mapNotNull null
            val file = filesByIdMap[expect.fileId] ?: return@mapNotNull null
            MissingActualInfo(mapping, expect.name, expect.qualifiedName, file.path)
        }
    
    override suspend fun upsertExpectActualMapping(mapping: ExpectActualMappingEntity) {
        val id = mapping.id ?: nextMappingId++
        mappings[id] = mapping.copy(id = id)
    }
    
    override suspend fun deleteExpectActualMappingsForSymbol(expectSymbolId: Long) {
        mappings.removeEntriesIf { it.value.expectSymbolId == expectSymbolId }
    }
    
    // ========================================================================
    // REFERENCES
    // ========================================================================
    
    override suspend fun getReferencesToSymbol(symbolId: Long): List<SymbolReferenceWithFile> =
        references.filter { it.symbolId == symbolId }.map { ref ->
            val file = filesByIdMap[ref.referencingFileId]
            SymbolReferenceWithFile(ref, file?.path ?: "")
        }
    
    override suspend fun getReferencesInFile(fileId: Long): List<SymbolReferenceWithSymbol> =
        references.filter { it.referencingFileId == fileId }.map { ref ->
            val symbol = symbols[ref.symbolId]
            SymbolReferenceWithSymbol(ref, symbol?.name ?: "", symbol?.qualifiedName ?: "")
        }
    
    override suspend fun insertReference(reference: SymbolReferenceEntity) {
        val id = reference.id ?: nextReferenceId++
        references.add(reference.copy(id = id))
    }
    
    override suspend fun deleteReferencesInFile(fileId: Long) {
        references.removeItemsIf { it.referencingFileId == fileId }
    }
    
    // ========================================================================
    // DIAGNOSTICS
    // ========================================================================
    
    override suspend fun getDiagnosticsForFile(fileId: Long): List<PersistedDiagnosticEntity> =
        diagnostics.values.filter { it.fileId == fileId }
    
    override fun getAllDiagnosticsFlow(): Flow<List<PersistedDiagnosticWithFile>> = diagnosticsFlow
    
    override suspend fun getDiagnosticsByCategory(category: String): List<PersistedDiagnosticWithFile> =
        diagnostics.values.filter { it.category == category }.map { diag ->
            val file = filesByIdMap[diag.fileId]
            PersistedDiagnosticWithFile(diag, file?.path ?: "")
        }
    
    override suspend fun getDiagnosticsBySeverity(severity: String): List<PersistedDiagnosticWithFile> =
        diagnostics.values.filter { it.severity == severity }.map { diag ->
            val file = filesByIdMap[diag.fileId]
            PersistedDiagnosticWithFile(diag, file?.path ?: "")
        }
    
    override suspend fun upsertDiagnostic(diagnostic: PersistedDiagnosticEntity) {
        diagnostics[diagnostic.id] = diagnostic
        updateDiagnosticsFlow()
    }
    
    override suspend fun suppressDiagnostic(diagnosticId: String, reason: String) {
        diagnostics[diagnosticId]?.let {
            diagnostics[diagnosticId] = it.copy(isSuppressed = true, suppressionReason = reason)
            updateDiagnosticsFlow()
        }
    }
    
    override suspend fun deleteDiagnosticsForFile(fileId: Long) {
        diagnostics.removeEntriesIf { it.value.fileId == fileId }
        updateDiagnosticsFlow()
    }
    
    private fun updateDiagnosticsFlow() {
        diagnosticsFlow.value = diagnostics.values.map { diag ->
            val file = filesByIdMap[diag.fileId]
            PersistedDiagnosticWithFile(diag, file?.path ?: "")
        }
    }
    
    // ========================================================================
    // REFACTOR SUGGESTIONS
    // ========================================================================
    
    override suspend fun getPendingRefactors(): List<PersistedRefactorEntity> =
        refactors.values.filter { !it.isApplied && !it.isDismissed }
    
    override suspend fun getRefactorsByCategory(category: String): List<PersistedRefactorEntity> =
        refactors.values.filter { it.category == category }
    
    override suspend fun upsertRefactor(refactor: PersistedRefactorEntity) {
        refactors[refactor.id] = refactor
    }
    
    override suspend fun markRefactorApplied(refactorId: String) {
        refactors[refactorId]?.let {
            refactors[refactorId] = it.copy(isApplied = true)
        }
    }
    
    override suspend fun dismissRefactor(refactorId: String, reason: String) {
        refactors[refactorId]?.let {
            refactors[refactorId] = it.copy(isDismissed = true, dismissalReason = reason)
        }
    }
    
    // ========================================================================
    // SETTINGS
    // ========================================================================
    
    override suspend fun getSetting(key: String): String? = settings[key]
    
    override suspend fun setSetting(key: String, value: String) {
        settings[key] = value
    }
    
    // ========================================================================
    // SHARING SNAPSHOTS
    // ========================================================================
    
    override suspend fun getLatestSharingSnapshot(repoPath: String): SharingSnapshotEntity? =
        snapshots.filter { it.repoPath == repoPath }.maxByOrNull { it.timestamp }
    
    override suspend fun getSharingHistory(repoPath: String, limit: Long): List<SharingSnapshotEntity> =
        snapshots.filter { it.repoPath == repoPath }
            .sortedByDescending { it.timestamp }
            .take(limit.toInt())
    
    override suspend fun insertSharingSnapshot(snapshot: SharingSnapshotEntity) {
        val id = snapshot.id ?: nextSnapshotId++
        snapshots.add(snapshot.copy(id = id))
    }
    
    // ========================================================================
    // TRANSACTIONS
    // ========================================================================
    
    override suspend fun <T> transaction(block: suspend () -> T): T = block()
}
