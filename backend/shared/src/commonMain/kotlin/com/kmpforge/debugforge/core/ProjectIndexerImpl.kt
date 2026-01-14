package com.kmpforge.debugforge.core

import com.kmpforge.debugforge.persistence.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * Implementation of ProjectIndexer that indexes source code for fast querying.
 * 
 * Stub implementation for initial compilation.
 * TODO: Implement full indexing logic.
 */
class ProjectIndexerImpl(
    private val dao: RepoIndexDao,
    private val symbolExtractor: SymbolExtractor
) : ProjectIndexer {
    
    private val _indexingState = MutableStateFlow<IndexingState>(IndexingState.Idle)
    override val indexingState: StateFlow<IndexingState> = _indexingState.asStateFlow()
    
    override suspend fun index(repository: ParsedRepository, forceFullReindex: Boolean): IndexingResult {
        val startTime = Clock.System.now().toEpochMilliseconds()
        
        try {
            _indexingState.value = IndexingState.InProgress(
                phase = IndexingPhase.PARSING,
                progress = 0f,
                currentFile = null,
                filesProcessed = 0,
                totalFiles = repository.kotlinFiles.size
            )
            
            // Store repository metadata
            val gitInfo = repository.gitInfo
            dao.upsertRepoMetadata(RepoMetadataEntity(
                repoPath = repository.rootPath,
                gitRemoteUrl = gitInfo?.remoteUrl,
                gitBranch = gitInfo?.currentBranch,
                gitCommitHash = gitInfo?.headCommit,
                lastIndexedAt = startTime,
                kotlinVersion = repository.rootBuildConfig.kotlinVersion,
                gradleVersion = repository.rootBuildConfig.gradleVersion,
                moduleCount = repository.modules.size,
                fileCount = repository.kotlinFiles.size,
                sharedCodePercentage = 0f
            ))
            
            // Store modules and source sets
            for (module in repository.modules) {
                val hasCommonMain = module.sourceSets.any { it.name.contains("common", ignoreCase = true) }
                
                dao.upsertModule(ModuleEntity(
                    gradlePath = module.gradlePath,
                    repoPath = repository.rootPath,
                    displayName = module.name,
                    absolutePath = module.path,
                    buildFilePath = module.buildFilePath,
                    isKmpModule = true,
                    hasCommonMain = hasCommonMain,
                    moduleType = "KMP",
                    indexedAt = startTime
                ))
                
                // Store source sets for this module
                for (sourceSet in module.sourceSets) {
                    dao.upsertSourceSet(SourceSetEntity(
                        moduleGradlePath = module.gradlePath,
                        name = sourceSet.name,
                        platform = sourceSet.platform.name,
                        directoryPath = sourceSet.kotlinPath ?: "",
                        fileCount = sourceSet.files.size,
                        lineCount = sourceSet.files.sumOf { it.lineCount }
                    ))
                }
            }
            
            var totalSymbols = 0
            var expectCount = 0
            var actualCount = 0
            
            repository.kotlinFiles.forEachIndexed { index, file ->
                _indexingState.value = IndexingState.InProgress(
                    phase = IndexingPhase.EXTRACTING_SYMBOLS,
                    progress = index.toFloat() / repository.kotlinFiles.size,
                    currentFile = file.absolutePath,
                    filesProcessed = index,
                    totalFiles = repository.kotlinFiles.size
                )
                
                // Extract symbols to determine expect/actual status
                val extractionResult = symbolExtractor.extract(file)
                val hasExpect = extractionResult.symbols.any { it.isExpect }
                val hasActual = extractionResult.symbols.any { it.isActual }
                
                // Store the file
                val fileEntity = IndexedFileEntity(
                    path = file.absolutePath,
                    relativePath = file.relativePath,
                    moduleGradlePath = file.moduleGradlePath,
                    sourceSet = file.sourceSetName,
                    packageName = file.packageName,
                    fileHash = file.hash,
                    lineCount = file.lineCount,
                    lastIndexedAt = Clock.System.now().toEpochMilliseconds(),
                    hasExpectDecls = hasExpect,
                    hasActualDecls = hasActual
                )
                
                val fileId = dao.insertFile(fileEntity)
                
                // Store extracted symbols
                extractionResult.symbols.forEach { symbol ->
                    totalSymbols++
                    if (symbol.isExpect) expectCount++
                    if (symbol.isActual) actualCount++
                    
                    dao.insertSymbol(IndexedSymbolEntity(
                        fileId = fileId,
                        name = symbol.name,
                        qualifiedName = symbol.qualifiedName,
                        kind = symbol.kind,
                        visibility = symbol.visibility,
                        isExpect = symbol.isExpect,
                        isActual = symbol.isActual,
                        isSuspend = symbol.isSuspend,
                        isInline = symbol.isInline,
                        isDataClass = symbol.isDataClass,
                        isSealed = symbol.isSealed,
                        isCompanion = symbol.isCompanion,
                        isExtension = symbol.isExtension,
                        startLine = symbol.startLine,
                        endLine = symbol.endLine,
                        startColumn = symbol.startColumn,
                        endColumn = symbol.endColumn,
                        signature = symbol.signature,
                        parentSymbolId = symbol.parentSymbolId,
                        annotations = symbol.annotations
                    ))
                }
            }
            
            val endTime = Clock.System.now().toEpochMilliseconds()
            val stats = IndexingStats(
                totalFiles = repository.kotlinFiles.size,
                totalSymbols = totalSymbols,
                totalExpectDeclarations = expectCount,
                totalActualImplementations = actualCount,
                totalReferences = 0,
                indexedAt = endTime,
                durationMs = endTime - startTime
            )
            
            _indexingState.value = IndexingState.Completed(stats)
            
            return IndexingResult(
                success = true,
                stats = stats,
                errors = emptyList()
            )
            
        } catch (e: Exception) {
            _indexingState.value = IndexingState.Failed(e.message ?: "Unknown error")
            
            return IndexingResult(
                success = false,
                stats = IndexingStats(0, 0, 0, 0, 0, 0, 0),
                errors = listOf(IndexingError(
                    filePath = "",
                    line = null,
                    message = e.message ?: "Unknown error",
                    isFatal = true
                ))
            )
        }
    }
    
    override suspend fun indexDelta(repository: ParsedRepository): IndexingDeltaResult {
        // Stub: just do a full reindex
        index(repository, true)
        return IndexingDeltaResult(
            filesAdded = repository.kotlinFiles.size,
            filesModified = 0,
            filesRemoved = 0,
            symbolsAdded = 0,
            symbolsModified = 0,
            symbolsRemoved = 0,
            durationMs = 0
        )
    }
    
    override suspend fun clearIndex(repoPath: String) {
        dao.clearRepository(repoPath)
    }
    
    override suspend fun getStats(repoPath: String): IndexingStats? {
        // Stub: return null for now
        return null
    }
}
