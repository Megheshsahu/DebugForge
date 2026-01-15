package com.kmpforge.debugforge.state

import com.kmpforge.debugforge.ai.RefactorRuleEngine
import com.kmpforge.debugforge.ai.RefactorSuggestion
import com.kmpforge.debugforge.ai.DiffHunk
import com.kmpforge.debugforge.ai.DiffLineType
import com.kmpforge.debugforge.analysis.*
import com.kmpforge.debugforge.core.*
import com.kmpforge.debugforge.diagnostics.*
import com.kmpforge.debugforge.persistence.*
import com.kmpforge.debugforge.preview.*
import com.kmpforge.debugforge.utils.DebugForgeLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock

/**
 * Main orchestrator for DebugForge.
 *
 * This is the central controller that:
 * 1. Exposes `StateFlow<DebugForgeState>` to the frontend
 * 2. Coordinates the pipeline: RepoLoader → ProjectIndexer → DiagnosticEngine → RefactorRuleEngine
 * 3. Updates state atomically
 */
class DebugForgeController(
    private val repoLoader: RepoLoader,
    private val projectIndexer: ProjectIndexer,
    private val diagnosticEngine: DiagnosticEngine,
    private val diagnosticEmitter: DiagnosticEmitter,
    private val refactorEngine: RefactorRuleEngine,
    private val previewOrchestrator: PreviewOrchestrator,
    private val dao: RepoIndexDao,
    private val fileSystem: FileSystem,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    
    private val _state = MutableStateFlow(DebugForgeState())
    
    /**
     * The main state exposed to the frontend.
     */
    val state: StateFlow<DebugForgeState> = _state.asStateFlow()
    
    /**
     * Real-time diagnostic events stream.
     */
    val diagnosticEvents: SharedFlow<DiagnosticEvent> = diagnosticEmitter.diagnosticStream
    
    /**
     * Preview sessions flow.
     */
    val previewSessions: StateFlow<Map<String, PreviewSession>> = previewOrchestrator.sessions
    
    init {
        // Subscribe to diagnostic event stream and update state
        scope.launch {
            diagnosticEmitter.diagnosticStream.collect { event ->
                when (event) {
                    is DiagnosticEvent.Added -> updateDiagnostics { current ->
                        current + event.diagnostic
                    }
                    is DiagnosticEvent.Resolved -> updateDiagnostics { current ->
                        current.filter { it.id != event.diagnosticId }
                    }
                    is DiagnosticEvent.Dismissed -> updateDiagnostics { current ->
                        current.filter { it.id != event.diagnosticId }
                    }
                    is DiagnosticEvent.FileClear -> updateDiagnostics { current ->
                        current.filter { it.location.filePath != event.filePath }
                    }
                    is DiagnosticEvent.Progress -> {
                        _state.update { it.copy(
                            analysisProgress = event.progress.percentage
                        ) }
                    }
                }
            }
        }
        
        // Subscribe to project indexer state
        scope.launch {
            projectIndexer.indexingState.collect { indexingState ->
                when (indexingState) {
                    is IndexingState.Idle -> {} // No update needed
                    is IndexingState.InProgress -> {
                        _state.update { it.copy(
                            repoStatus = RepoStatus.Indexing(
                                totalFiles = indexingState.totalFiles,
                                indexedFiles = indexingState.filesProcessed,
                                currentFile = indexingState.currentFile ?: ""
                            )
                        ) }
                    }
                    is IndexingState.Completed -> {
                        _state.update { it.copy(
                            repoStatus = RepoStatus.Analyzing(
                                currentAnalyzer = "Starting analysis...",
                                progress = 0f
                            )
                        ) }
                    }
                    is IndexingState.Failed -> {
                        _state.update { it.copy(
                            repoStatus = RepoStatus.Failed(
                                error = indexingState.error,
                                recoverable = true
                            ),
                            fatalError = FatalError(
                                code = "INDEXING_FAILED",
                                message = indexingState.error,
                                timestamp = Clock.System.now().toEpochMilliseconds()
                            )
                        ) }
                    }
                }
            }
        }
    }
    
    /**
     * Loads a repository from a local path.
     */
    suspend fun loadRepository(path: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        DebugForgeLogger.debug("DebugForgeController", "Starting loadRepository for path: $path")
        _state.update { it.copy(
            repoStatus = RepoStatus.Loading(path = path, filesScanned = 0)
        ) }
        
        // Collect loading progress
        scope.launch {
            repoLoader.loadingState.collect { loadingState ->
                when (loadingState) {
                    is LoadingState.Idle -> {}
                    is LoadingState.InProgress -> {
                        DebugForgeLogger.debug("DebugForgeController", "Loading progress: ${loadingState.operation} - ${loadingState.progress}")
                        _state.update { it.copy(
                            repoStatus = RepoStatus.Loading(
                                path = path,
                                filesScanned = (loadingState.progress * 100).toInt()
                            )
                        ) }
                    }
                    is LoadingState.Completed -> {
                        DebugForgeLogger.debug("DebugForgeController", "Loading completed")
                        // Will be handled in main flow
                    }
                    is LoadingState.Error -> {
                        DebugForgeLogger.error("DebugForgeController", "Loading error: ${loadingState.message}")
                        _state.update { it.copy(
                            repoStatus = RepoStatus.Failed(
                                error = loadingState.message,
                                recoverable = true
                            ),
                            fatalError = FatalError(
                                code = "LOADING_FAILED",
                                message = loadingState.message,
                                timestamp = Clock.System.now().toEpochMilliseconds()
                            )
                        ) }
                    }
                }
            }
        }
        
        try {
            DebugForgeLogger.debug("DebugForgeController", "Step 1: Loading and parsing repository")
            // Step 1: Load and parse repository
            val result = repoLoader.loadLocalRepository(path)
            val parsedRepo = result.getOrThrow()
            DebugForgeLogger.debug("DebugForgeController", "Repository parsed successfully. Modules: ${parsedRepo.modules.size}, Source files: ${parsedRepo.sourceFiles.size}")
            
            // Convert to ModuleInfo list
            val modules = parsedRepo.modules.map { module ->
                convertToModuleInfo(module)
            }
            
            DebugForgeLogger.debug("DebugForgeController", "Step 2: Starting indexing")
            _state.update { it.copy(
                modules = modules,
                repoStatus = RepoStatus.Indexing(
                    totalFiles = parsedRepo.sourceFiles.size,
                    indexedFiles = 0,
                    currentFile = "Starting indexing..."
                )
            ) }
            
            // Step 2: Index the repository
            val indexResult = projectIndexer.index(parsedRepo)
            DebugForgeLogger.debug("DebugForgeController", "Indexing completed")
            
            DebugForgeLogger.debug("DebugForgeController", "Step 3: Starting analysis")
            // Step 3: Analyze for diagnostics
            _state.update { it.copy(
                repoStatus = RepoStatus.Analyzing(
                    currentAnalyzer = "Running analyzers...",
                    progress = 0f
                )
            ) }
            
            diagnosticEngine.analyze(path)
            val diagnostics = diagnosticEngine.diagnostics.value
            DebugForgeLogger.debug("DebugForgeController", "Analysis completed. Diagnostics: ${diagnostics.size}")
            
            // Emit all diagnostics
            for (diagnostic in diagnostics) {
                diagnosticEmitter.emit(diagnostic)
            }
            
            DebugForgeLogger.debug("DebugForgeController", "Step 4: Generating refactoring suggestions")
            // Step 4: Generate refactoring suggestions
            val suggestions = refactorEngine.analyzeForRefactoring(path)
            val diagnosticSuggestions = refactorEngine.generateFromDiagnostics(diagnostics)
            val allSuggestions = (suggestions + diagnosticSuggestions).distinctBy { it.id }
            DebugForgeLogger.debug("DebugForgeController", "Refactoring suggestions generated: ${allSuggestions.size}")
            
            DebugForgeLogger.debug("DebugForgeController", "Step 5: Calculating shared code metrics")
            // Step 5: Calculate shared code metrics
            val sharedCodeMetrics = calculateSharedCodeMetrics(modules)
            
            DebugForgeLogger.debug("DebugForgeController", "Updating final state")
            // Update final state
            _state.update { it.copy(
                modules = modules,
                diagnostics = diagnostics,
                refactorSuggestions = allSuggestions,
                sharedCodeMetrics = sharedCodeMetrics,
                previews = PreviewState.Inactive,
                repoStatus = RepoStatus.Ready(
                    repoPath = path,
                    repoName = parsedRepo.name,
                    totalFiles = parsedRepo.sourceFiles.size,
                    totalModules = modules.size,
                    loadedAt = now
                ),
                fatalError = null,
                lastUpdated = Clock.System.now().toEpochMilliseconds()
            ) }
            
            DebugForgeLogger.debug("DebugForgeController", "Repository load completed successfully")
            
        } catch (e: Exception) {
            DebugForgeLogger.error("DebugForgeController", "Exception during repository load: ${e.message}", e)
            _state.update { it.copy(
                repoStatus = RepoStatus.Failed(
                    error = e.message ?: "Unknown error",
                    recoverable = false
                ),
                fatalError = FatalError(
                    code = "UNEXPECTED_ERROR",
                    message = e.message ?: "An unexpected error occurred",
                    stackTrace = e.stackTraceToString(),
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            ) }
        }
    }
    
    /**
     * Clones a repository from a Git URL.
     */
    suspend fun cloneRepository(url: String, localPath: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        _state.update { it.copy(
            repoStatus = RepoStatus.Cloning(
                url = url,
                progress = 0f,
                currentOperation = "Starting clone..."
            )
        ) }
        
        try {
            val result = repoLoader.cloneAndLoad(url, localPath)
            val parsedRepo = result.getOrThrow()
            
            val modules = parsedRepo.modules.map { module ->
                convertToModuleInfo(module)
            }
            
            _state.update { it.copy(
                modules = modules,
                repoStatus = RepoStatus.Indexing(
                    totalFiles = parsedRepo.sourceFiles.size,
                    indexedFiles = 0,
                    currentFile = "Starting indexing..."
                )
            ) }
            
            val indexResult = projectIndexer.index(parsedRepo)
            
            _state.update { it.copy(
                repoStatus = RepoStatus.Analyzing(
                    currentAnalyzer = "Running analyzers...",
                    progress = 0f
                )
            ) }
            
            diagnosticEngine.analyze(localPath)
            val diagnostics = diagnosticEngine.diagnostics.value
            for (diagnostic in diagnostics) {
                diagnosticEmitter.emit(diagnostic)
            }
            
            val suggestions = refactorEngine.analyzeForRefactoring(localPath)
            val diagnosticSuggestions = refactorEngine.generateFromDiagnostics(diagnostics)
            val allSuggestions = (suggestions + diagnosticSuggestions).distinctBy { it.id }
            
            val sharedCodeMetrics = calculateSharedCodeMetrics(modules)
            
            _state.update { it.copy(
                modules = modules,
                diagnostics = diagnostics,
                refactorSuggestions = allSuggestions,
                sharedCodeMetrics = sharedCodeMetrics,
                previews = PreviewState.Inactive,
                repoStatus = RepoStatus.Ready(
                    repoPath = localPath,
                    repoName = parsedRepo.name,
                    totalFiles = parsedRepo.sourceFiles.size,
                    totalModules = modules.size,
                    loadedAt = now
                ),
                fatalError = null,
                lastUpdated = Clock.System.now().toEpochMilliseconds()
            ) }
            
        } catch (e: Exception) {
            _state.update { it.copy(
                repoStatus = RepoStatus.Failed(
                    error = e.message ?: "Clone failed",
                    recoverable = true
                ),
                fatalError = FatalError(
                    code = "CLONE_FAILED",
                    message = e.message ?: "Failed to clone repository",
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            ) }
        }
    }
    
    /**
     * Re-analyzes the currently loaded repository.
     */
    suspend fun refresh() {
        val currentState = _state.value
        val readyStatus = currentState.repoStatus as? RepoStatus.Ready ?: return
        
        val repoPath = readyStatus.repoPath
        
        _state.update { it.copy(
            repoStatus = RepoStatus.Analyzing(
                currentAnalyzer = "Re-analyzing...",
                progress = 0f
            )
        ) }
        
        try {
            diagnosticEngine.analyze(repoPath)
            val diagnostics = diagnosticEngine.diagnostics.value
            
            val suggestions = refactorEngine.analyzeForRefactoring(repoPath)
            val diagnosticSuggestions = refactorEngine.generateFromDiagnostics(diagnostics)
            val allSuggestions = (suggestions + diagnosticSuggestions).distinctBy { it.id }
            
            val sharedCodeMetrics = calculateSharedCodeMetrics(currentState.modules)
            
            _state.update { it.copy(
                diagnostics = diagnostics,
                refactorSuggestions = allSuggestions,
                sharedCodeMetrics = sharedCodeMetrics,
                repoStatus = readyStatus,
                lastUpdated = Clock.System.now().toEpochMilliseconds()
            ) }
            
        } catch (e: Exception) {
            _state.update { it.copy(
                repoStatus = readyStatus,
                fatalError = FatalError(
                    code = "REFRESH_FAILED",
                    message = e.message ?: "Refresh failed",
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            ) }
        }
    }
    
    /**
     * Applies a refactoring suggestion.
     */
    suspend fun applyRefactoring(suggestionId: String): Boolean {
        val suggestion = _state.value.refactorSuggestions.find { it.id == suggestionId }
            ?: return false

        if (!suggestion.isAutoApplicable) {
            return false
        }

        try {
            // Apply the file changes
            for (change in suggestion.changes) {
                val filePath = change.filePath
                val currentContent = fileSystem.readFile(filePath)

                // Apply all hunks in this change
                var modifiedContent = currentContent
                for (hunk in change.hunks) {
                    modifiedContent = applyDiffHunk(modifiedContent, hunk)
                }

                // Write the modified content back to the file
                fileSystem.writeFile(filePath, modifiedContent)
            }

            dao.markRefactorApplied(suggestionId)

            _state.update { state ->
                state.copy(
                    refactorSuggestions = state.refactorSuggestions.filter { it.id != suggestionId }
                )
            }

            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Applies a diff hunk to file content.
     */
    private fun applyDiffHunk(content: String, hunk: DiffHunk): String {
        val lines = content.lines().toMutableList()
        val result = mutableListOf<String>()

        // Find the context around the hunk
        val startLine = hunk.originalStart - 1 // Convert to 0-based indexing
        val endLine = startLine + hunk.originalCount

        // Add lines before the hunk
        for (i in 0 until startLine) {
            if (i < lines.size) {
                result.add(lines[i])
            }
        }

        // Apply the changes from the hunk
        var originalIndex = startLine
        for (line in hunk.lines) {
            when (line.type) {
                DiffLineType.CONTEXT -> {
                    // Keep context lines
                    if (originalIndex < lines.size) {
                        result.add(lines[originalIndex])
                        originalIndex++
                    }
                }
                DiffLineType.ADDITION -> {
                    // Add new lines
                    result.add(line.content)
                }
                DiffLineType.DELETION -> {
                    // Skip deleted lines
                    if (originalIndex < lines.size) {
                        originalIndex++
                    }
                }
            }
        }

        // Add remaining lines after the hunk
        for (i in originalIndex until lines.size) {
            result.add(lines[i])
        }

        return result.joinToString("\n")
    }
    
    /**
     * Dismisses a refactoring suggestion.
     */
    suspend fun dismissRefactoring(suggestionId: String, reason: String = "User dismissed") {
        dao.dismissRefactor(suggestionId, reason)
        
        _state.update { state ->
            state.copy(
                refactorSuggestions = state.refactorSuggestions.filter { it.id != suggestionId }
            )
        }
    }
    
    /**
     * Suppresses a diagnostic.
     */
    suspend fun suppressDiagnostic(diagnosticId: String, reason: String = "User suppressed") {
        val diagnostic = _state.value.diagnostics.find { it.id == diagnosticId }
            ?: return
        
        val fileEntity = dao.getFile(diagnostic.location.filePath)
        if (fileEntity != null) {
            dao.suppressDiagnostic(diagnosticId, reason)
        }
        
        diagnosticEmitter.emitDismissed(diagnosticId)
    }
    
    /**
     * Starts a preview session.
     */
    suspend fun startPreview(previewId: String, platform: SourceSetPlatform): PreviewSession? {
        val discoveredPreviews = previewOrchestrator.orchestratorState.value.discoveredPreviews
        val preview = discoveredPreviews.find { it.id == previewId } ?: return null
        
        val session = previewOrchestrator.startPreview(preview, platform)
        
        _state.update { state ->
            state.copy(
                previews = PreviewState.Active(
                    sessionId = session.id,
                    platforms = emptyList(),
                    startedAt = Clock.System.now().toEpochMilliseconds(),
                    diagnosticOverlayEnabled = false,
                    hotReloadEnabled = false
                )
            )
        }
        
        return session
    }
    
    /**
     * Stops a preview session.
     */
    suspend fun stopPreview(sessionId: String) {
        previewOrchestrator.stopPreview(sessionId)
        
        _state.update { state ->
            state.copy(previews = PreviewState.Inactive)
        }
    }
    
    /**
     * Notifies about file changes for hot-reload.
     */
    suspend fun notifyFileChanged(filePath: String) {
        previewOrchestrator.notifyFileChanged(filePath)
    }
    
    /**
     * Gets filtered diagnostics.
     */
    fun getFilteredDiagnostics(filter: DiagnosticFilter): List<Diagnostic> {
        return diagnosticEmitter.filterDiagnostics(_state.value.diagnostics, filter)
    }
    
    /**
     * Clears any fatal error.
     */
    fun clearError() {
        _state.update { it.copy(fatalError = null) }
    }
    
    /**
     * Shuts down the controller and releases resources.
     */
    fun shutdown() {
        previewOrchestrator.shutdown()
        scope.cancel()
    }
    
    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================
    
    private fun updateDiagnostics(transform: (List<Diagnostic>) -> List<Diagnostic>) {
        _state.update { state ->
            state.copy(diagnostics = transform(state.diagnostics))
        }
    }
    
    private fun convertToModuleInfo(detected: DetectedModule): ModuleInfo {
        val sourceSets = detected.sourceSets.map { sourceSet ->
            SourceSetInfo(
                name = sourceSet.name,
                platform = sourceSet.platform,
                sourcePath = sourceSet.kotlinPath ?: "",
                resourcePath = sourceSet.resourcePath,
                kotlinFileCount = sourceSet.files.size,
                kotlinLinesOfCode = sourceSet.files.sumOf { it.lineCount },
                dependencies = emptyList()
            )
        }
        
        // Count files by language
        val allFiles = detected.sourceSets.flatMap { it.files }
        val kotlinFiles = allFiles.count { it.absolutePath.endsWith(".kt") || it.absolutePath.endsWith(".kts") }
        val javaFiles = allFiles.count { it.absolutePath.endsWith(".java") }
        val javascriptFiles = allFiles.count { it.absolutePath.endsWith(".js") || it.absolutePath.endsWith(".mjs") }
        val typescriptFiles = allFiles.count { it.absolutePath.endsWith(".ts") || it.absolutePath.endsWith(".tsx") }
        val pythonFiles = allFiles.count { it.absolutePath.endsWith(".py") }
        val rustFiles = allFiles.count { it.absolutePath.endsWith(".rs") }
        val goFiles = allFiles.count { it.absolutePath.endsWith(".go") }
        val cFiles = allFiles.count { it.absolutePath.endsWith(".c") || it.absolutePath.endsWith(".h") }
        val cppFiles = allFiles.count { it.absolutePath.endsWith(".cpp") || it.absolutePath.endsWith(".hpp") || it.absolutePath.endsWith(".cc") || it.absolutePath.endsWith(".cxx") }
        val csharpFiles = allFiles.count { it.absolutePath.endsWith(".cs") }
        val swiftFiles = allFiles.count { it.absolutePath.endsWith(".swift") }
        val scalaFiles = allFiles.count { it.absolutePath.endsWith(".scala") }
        val groovyFiles = allFiles.count { it.absolutePath.endsWith(".groovy") || it.absolutePath.endsWith(".gradle") }
        
        return ModuleInfo(
            id = detected.gradlePath,
            name = detected.name,
            path = detected.path,
            gradlePath = detected.gradlePath,
            sourceSets = sourceSets,
            dependencies = emptyList(),
            targets = emptySet(),
            hasCommonCode = sourceSets.any { it.platform == SourceSetPlatform.COMMON },
            buildConfig = BuildConfig(
                buildSystem = detected.buildFileType,
                kotlinVersion = null,
                composeEnabled = false,
                sqlDelightEnabled = false,
                kspProcessors = emptyList()
            ),
            fileStats = FileStats(
                totalFiles = allFiles.size,
                kotlinFiles = kotlinFiles,
                javaFiles = javaFiles,
                javascriptFiles = javascriptFiles,
                typescriptFiles = typescriptFiles,
                pythonFiles = pythonFiles,
                rustFiles = rustFiles,
                goFiles = goFiles,
                cFiles = cFiles,
                cppFiles = cppFiles,
                csharpFiles = csharpFiles,
                swiftFiles = swiftFiles,
                scalaFiles = scalaFiles,
                groovyFiles = groovyFiles,
                resourceFiles = 0,
                totalLinesOfCode = allFiles.sumOf { it.lineCount },
                kotlinLinesOfCode = allFiles.filter { it.absolutePath.endsWith(".kt") || it.absolutePath.endsWith(".kts") }.sumOf { it.lineCount },
                javaLinesOfCode = allFiles.filter { it.absolutePath.endsWith(".java") }.sumOf { it.lineCount },
                javascriptLinesOfCode = allFiles.filter { it.absolutePath.endsWith(".js") || it.absolutePath.endsWith(".mjs") }.sumOf { it.lineCount },
                typescriptLinesOfCode = allFiles.filter { it.absolutePath.endsWith(".ts") || it.absolutePath.endsWith(".tsx") }.sumOf { it.lineCount },
                pythonLinesOfCode = allFiles.filter { it.absolutePath.endsWith(".py") }.sumOf { it.lineCount },
                rustLinesOfCode = allFiles.filter { it.absolutePath.endsWith(".rs") }.sumOf { it.lineCount },
                goLinesOfCode = allFiles.filter { it.absolutePath.endsWith(".go") }.sumOf { it.lineCount },
                cLinesOfCode = allFiles.filter { it.absolutePath.endsWith(".c") || it.absolutePath.endsWith(".h") }.sumOf { it.lineCount },
                cppLinesOfCode = allFiles.filter { it.absolutePath.endsWith(".cpp") || it.absolutePath.endsWith(".hpp") || it.absolutePath.endsWith(".cc") || it.absolutePath.endsWith(".cxx") }.sumOf { it.lineCount },
                csharpLinesOfCode = allFiles.filter { it.absolutePath.endsWith(".cs") }.sumOf { it.lineCount },
                swiftLinesOfCode = allFiles.filter { it.absolutePath.endsWith(".swift") }.sumOf { it.lineCount },
                scalaLinesOfCode = allFiles.filter { it.absolutePath.endsWith(".scala") }.sumOf { it.lineCount },
                groovyLinesOfCode = allFiles.filter { it.absolutePath.endsWith(".groovy") || it.absolutePath.endsWith(".gradle") }.sumOf { it.lineCount }
            )
        )
    }
    
    private fun calculateSharedCodeMetrics(modules: List<ModuleInfo>): SharedCodeMetrics {
        var totalLines = 0
        var commonLines = 0
        
        val moduleRankings = mutableListOf<ModuleSharedRanking>()
        var rank = 1
        
        for (module in modules) {
            val moduleTotal = module.fileStats.kotlinLinesOfCode
            val moduleCommon = module.sourceSets
                .filter { it.platform == SourceSetPlatform.COMMON }
                .sumOf { it.kotlinLinesOfCode }
            
            totalLines += moduleTotal
            commonLines += moduleCommon
            
            if (moduleTotal > 0) {
                moduleRankings.add(ModuleSharedRanking(
                    moduleId = module.id,
                    moduleName = module.name,
                    sharedPercentage = (moduleCommon.toFloat() / moduleTotal * 100),
                    sharedLines = moduleCommon,
                    totalLines = moduleTotal,
                    rank = rank++
                ))
            }
        }
        
        val sharedPercentage = if (totalLines > 0) {
            commonLines.toFloat() / totalLines * 100
        } else 0f
        
        return SharedCodeMetrics(
            totalLinesOfCode = totalLines,
            sharedLinesOfCode = commonLines,
            sharedCodePercentage = sharedPercentage,
            platformBreakdown = PlatformCodeBreakdown.EMPTY,
            expectDeclarations = 0,
            actualImplementations = 0,
            expectActualCoverage = 0f,
            moduleRankings = moduleRankings.sortedByDescending { it.sharedPercentage },
            sharableCandidates = emptyList()
        )
    }
}

/**
 * Factory for creating a fully wired DebugForgeController.
 */
object DebugForgeControllerFactory {
    /**
     * Creates a controller with all dependencies wired up.
     */
    fun create(
        dao: RepoIndexDao,
        fileSystem: FileSystem,
        gitOperations: GitOperations,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ): DebugForgeController {
        // Create file system reader adapter first
        val fileSystemReader = object : FileSystemReader {
            override suspend fun readFile(path: String): String = fileSystem.readFile(path)
            override suspend fun exists(path: String): Boolean = fileSystem.exists(path)
        }
        
        val symbolExtractor = RegexSymbolExtractor()
        val repoLoader = RepoLoaderImpl(fileSystem, gitOperations)
        val projectIndexer = ProjectIndexerImpl(dao, symbolExtractor)
        
        val expectActualAnalyzer = ExpectActualAnalyzer(dao)
        val coroutineLeakDetector = CoroutineLeakDetector(dao, fileSystemReader)
        val wasmThreadSafetyAnalyzer = WasmThreadSafetyAnalyzer(dao, fileSystemReader)
        val apiMisuseAnalyzer = ApiMisuseAnalyzer(dao, fileSystemReader)
        
        val diagnosticEngine = DiagnosticEngine(
            expectActualAnalyzer = expectActualAnalyzer,
            coroutineLeakDetector = coroutineLeakDetector,
            wasmThreadSafetyAnalyzer = wasmThreadSafetyAnalyzer,
            apiMisuseAnalyzer = apiMisuseAnalyzer
        )
        
        val diagnosticEmitter = DiagnosticEmitter()
        
        val refactorEngine = RefactorRuleEngine(dao, fileSystemReader)
        
        val previewRunnerFactory = DefaultPreviewRunnerFactory()
        val previewOrchestrator = PreviewOrchestrator(dao, fileSystemReader, previewRunnerFactory)
        
        return DebugForgeController(
            repoLoader = repoLoader,
            projectIndexer = projectIndexer,
            diagnosticEngine = diagnosticEngine,
            diagnosticEmitter = diagnosticEmitter,
            refactorEngine = refactorEngine,
            previewOrchestrator = previewOrchestrator,
            dao = dao,
            fileSystem = fileSystem,
            dispatcher = dispatcher
        )
    }
}
