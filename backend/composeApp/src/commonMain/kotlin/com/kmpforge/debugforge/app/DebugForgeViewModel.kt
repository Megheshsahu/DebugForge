package com.kmpforge.debugforge.app

import com.kmpforge.debugforge.config.GitHubConfig
import com.kmpforge.debugforge.platform.PlatformFileSystem
import com.kmpforge.debugforge.sync.GitHubService
import com.kmpforge.debugforge.sync.SyncManager
import com.kmpforge.debugforge.sync.SyncResult
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

// Platform-specific temp directory
expect fun getTempDir(): String

// UI State
sealed class UiState {
    data object Idle : UiState()
    data object Settings : UiState()
    data class Loading(val message: String) : UiState()
    data class Ready(
        val repoId: String,
        val repoPath: String,
        val modules: List<ModuleDisplay>,
        val diagnostics: List<DiagnosticDisplay>,
        val suggestions: List<SuggestionDisplay>,
        val sharedCodePercent: Double
    ) : UiState()
    data class Error(val message: String) : UiState()
}

// Display models
data class ModuleDisplay(
    val name: String,
    val path: String,
    val fileCount: Int,
    val sourceSets: List<String>
)

data class DiagnosticDisplay(
    val id: String,
    val severity: String,
    val category: String,
    val message: String,
    val filePath: String,
    val line: Int,
    val codeSnippet: String? = null
)

data class SuggestionDisplay(
    val id: String,
    val title: String,
    val rationale: String,
    val beforeCode: String? = null,
    val afterCode: String? = null
)

class DebugForgeViewModel {
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val apiClient = DebugForgeApiClient()
    private val fileSystem = PlatformFileSystem()
    // Undo/Redo manager for reverting applied fixes
    val undoManager = UndoManager()
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }
    private val githubService: GitHubService? = if (GitHubConfig.ENABLE_GITHUB_SYNC && GitHubConfig.GITHUB_TOKEN.isNotEmpty()) {
        GitHubService(GitHubConfig.GITHUB_TOKEN, httpClient)
    } else null
    private val syncManager: SyncManager? = githubService?.let { SyncManager(it) }
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private val _isBackendConnected = MutableStateFlow(false)
    val isBackendConnected: StateFlow<Boolean> = _isBackendConnected.asStateFlow()
    private val _githubSyncEnabled = MutableStateFlow(GitHubConfig.ENABLE_GITHUB_SYNC)
    val githubSyncEnabled: StateFlow<Boolean> = _githubSyncEnabled.asStateFlow()
    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()
    private val _aiAnalysisEnabled = MutableStateFlow(true)
    val aiAnalysisEnabled: StateFlow<Boolean> = _aiAnalysisEnabled.asStateFlow()
    private val _aiSuggestions = MutableStateFlow<List<SuggestionDisplay>>(emptyList())
    val aiSuggestions: StateFlow<List<SuggestionDisplay>> = _aiSuggestions.asStateFlow()
    
    /**
     * Save API configuration
     */
    private val secureStorage: SecureStorage = createSecureStorage()

    init {
        // Start server automatically on app startup
        // startServer()  // Commented out to prevent automatic server cycling
    }
    fun startServer() {
        // Prevent starting server multiple times
        if (_isServerRunning.value) {
            _syncStatus.value = "ℹ️ Server is already running"
            return
        }
        
        _syncStatus.value = "Starting server..."
        val groqApiKey = secureStorage.getString("groqApiKey") ?: ""
        val githubToken = secureStorage.getString("githubToken") ?: ""
        _syncStatus.value = "API keys loaded: groq=${groqApiKey.isNotBlank()}, github=${githubToken.isNotBlank()}"

        // Server can start even without API keys, but warn user
        if (groqApiKey.isBlank()) {
            _syncStatus.value = "⚠️ Warning: No Groq API key configured. AI analysis will be limited."
        }

        val serverStarted = startServerPlatform(this, groqApiKey, githubToken)
        if (!serverStarted) {
            _syncStatus.value = "❌ Failed to start server - check logs for details"
            _isServerRunning.value = false
            _isBackendConnected.value = false
            return
        }
        
        // Delay to let server start
        scope.launch {
            _syncStatus.value = "⏳ Waiting for server to start..."
            kotlinx.coroutines.delay(2000) // Reduced delay since we know server started
            val running = isServerRunningPlatform(this@DebugForgeViewModel)
            println("DEBUG: Server running check result: $running")
            if (running) {
                _syncStatus.value = "✅ Server started successfully on port 18999"
                _isServerRunning.value = true
                // Update backend connection status
                checkBackendConnection()
            } else {
                _syncStatus.value = "❌ Server failed to bind to port - check if port 18999 is available"
                _isServerRunning.value = false
                _isBackendConnected.value = false
            }
        }
    }
    fun stopServer() {
        stopServerPlatform(this)
        _isServerRunning.value = false
        _isBackendConnected.value = false
    }
    fun setServerRunning(running: Boolean) {
        _isServerRunning.value = running
    }
    
    private fun checkBackendConnection() {
        scope.launch {
            _isBackendConnected.value = apiClient.checkHealth()
        }
    }
    
    fun loadRepo(input: String) {
        // Launch in ViewModel's scope (not tied to Compose lifecycle)
        scope.launch {
            _uiState.value = UiState.Loading("Loading repository...")
            
            try {
                val trimmedInput = input.trim()
                
                if (apiClient.isGitHubUrl(trimmedInput)) {
                    // GitHub URL - clone first
                    _uiState.value = UiState.Loading("Cloning from GitHub...")
                    
                    val repoName = trimmedInput.substringAfterLast("/").removeSuffix(".git")
                    val localPath = getTempDir() + "/debugforge-repos/$repoName"
                    
                    when (val cloneResult = apiClient.cloneRepo(trimmedInput, localPath)) {
                        is DebugForgeApiClient.CloneResult.Success -> {
                            // Clone completed, fetch results
                            fetchAndShowResults(localPath, "cloned-repo")
                        }
                        is DebugForgeApiClient.CloneResult.Failed -> {
                            // If clone failed because dir exists, try loading it instead
                            if (cloneResult.error.contains("already exists", ignoreCase = true) || 
                                cloneResult.error.contains("exists and is not an empty", ignoreCase = true)) {
                                _uiState.value = UiState.Loading("Using existing clone, analyzing...")
                                
                                // The cloned repo might have the KMP project in a subdirectory
                                // Try common patterns: root, backend/, app/, etc.
                                val pathsToTry = listOf(
                                    localPath,
                                    "$localPath/backend",
                                    "$localPath/app",
                                    "$localPath/project"
                                )
                                
                                var loadSuccess = false
                                var lastError = ""
                                
                                for (tryPath in pathsToTry) {
                                    when (val loadResult = apiClient.loadRepo(tryPath)) {
                                        is DebugForgeApiClient.LoadResult.Success -> {
                                            fetchAndShowResults(tryPath, "cloned-repo")
                                            loadSuccess = true
                                            break
                                        }
                                        is DebugForgeApiClient.LoadResult.Failed -> {
                                            lastError = loadResult.error
                                            continue
                                        }
                                    }
                                }
                                
                                if (!loadSuccess) {
                                    _uiState.value = UiState.Error("Could not find KMP project in cloned repo. Last error: $lastError")
                                }
                            } else {
                                _uiState.value = UiState.Error("GitHub clone failed: ${cloneResult.error}")
                            }
                        }
                    }
                } else {
                    // Local path
                    _uiState.value = UiState.Loading("Analyzing local repository...")
                    
                    when (val loadResult = apiClient.loadRepo(trimmedInput)) {
                        is DebugForgeApiClient.LoadResult.Success -> {
                            // Load completed, fetch results
                            fetchAndShowResults(trimmedInput, "local-repo")
                        }
                        is DebugForgeApiClient.LoadResult.Failed -> {
                            _uiState.value = UiState.Error("Load failed: ${loadResult.error}")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = UiState.Error("Error: ${e.message}")
            }
        }
    }
    
    private suspend fun fetchAndShowResults(repoPath: String, repoId: String) {
        _uiState.value = UiState.Loading("Fetching analysis results...")
        
        val diagnostics = apiClient.getGlobalDiagnostics()
        val modules = apiClient.getGlobalModules()
        var suggestions = apiClient.getGlobalSuggestions()
        val metrics = apiClient.getGlobalMetrics()
        
        // Run AI analysis if enabled
        if (_aiAnalysisEnabled.value) {
            _uiState.value = UiState.Loading("Running AI analysis with Grok...")
            
            // Analyze key files from each module
            val aiSuggestionsList = mutableListOf<SuggestionDisplay>()
            
            for (module in modules.take(3)) { // Limit to first 3 modules
                // Try to find main Kotlin files to analyze
                val moduleContext = "KMP module: ${module.name}, sourceSets: ${module.sourceSets.joinToString()}"
                
                // Get sample code from diagnostics for this module
                val moduleDiagnostics = diagnostics.filter { it.filePath.contains(module.path) }
                
                for (diagnostic in moduleDiagnostics.take(2)) { // Limit per module
                    if (diagnostic.codeSnippet?.isNotEmpty() == true) {
                        val aiResult = apiClient.analyzeWithAI(
                            code = diagnostic.codeSnippet,
                            fileName = diagnostic.filePath.substringAfterLast("/").substringAfterLast("\\"),
                            context = moduleContext
                        )
                        
                        // Convert AI suggestions to SuggestionDisplay
                        aiResult.suggestions.forEach { aiSugg ->
                            aiSuggestionsList.add(
                                SuggestionDisplay(
                                    id = "ai-${System.currentTimeMillis()}-${aiSuggestionsList.size}",
                                    title = "🤖 ${aiSugg.title}",
                                    rationale = aiSugg.rationale,
                                    beforeCode = aiSugg.beforeCode,
                                    afterCode = aiSugg.afterCode
                                )
                            )
                        }
                    }
                }
            }
            
            // Merge AI suggestions with rule-based suggestions
            _aiSuggestions.value = aiSuggestionsList
            suggestions = suggestions + aiSuggestionsList
        }
        
        _uiState.value = UiState.Ready(
            repoId = repoId,
            repoPath = repoPath,
            modules = modules,
            diagnostics = diagnostics,
            suggestions = suggestions,
            sharedCodePercent = metrics?.sharedCodePercent ?: 0.0
        )
    }
    
    fun reset() {
        _uiState.value = UiState.Idle
        _syncStatus.value = null
        checkBackendConnection()
    }
    
    /**
     * Apply fix via GitHub (creates branch and PR)
     */
    fun applyFixViaGitHub(
        owner: String,
        repo: String,
        filePath: String,
        newContent: String,
        fixDescription: String
    ) {
        if (syncManager == null) {
            _syncStatus.value = "❌ GitHub sync not enabled. Set token in GitHubConfig."
            return
        }
        
        scope.launch {
            _syncStatus.value = "🔄 Creating branch and applying fix..."
            
            when (val result = syncManager.applyAndSync(
                owner = owner,
                repo = repo,
                filePath = filePath,
                newContent = newContent,
                fixDescription = fixDescription
            )) {
                is SyncResult.Success -> {
                    _syncStatus.value = "✅ Pull Request created: #${result.prNumber}\n${result.prUrl}"
                }
                is SyncResult.Failed -> {
                    _syncStatus.value = "❌ Failed: ${result.error}"
                }
            }
        }
    }
    
    /**
     * Load project from GitHub and analyze
     */
    fun loadFromGitHub(owner: String, repo: String) {
        scope.launch {
            _uiState.value = UiState.Loading("Cloning from GitHub...")
            
            val githubUrl = "https://github.com/$owner/$repo"
            loadRepo(githubUrl)
        }
    }
    
    fun clearSyncStatus() {
        _syncStatus.value = null
    }
    
    /**
     * Toggle AI analysis on/off
     */
    fun toggleAiAnalysis() {
        _aiAnalysisEnabled.value = !_aiAnalysisEnabled.value
    }
    
    /**
     * Set AI analysis enabled/disabled
     */
    fun setAiAnalysisEnabled(enabled: Boolean) {
        _aiAnalysisEnabled.value = enabled
    }
    
    /**
     * Manually trigger AI analysis on specific code
     */
    fun analyzeCodeWithAI(code: String, fileName: String = "code.kt") {
        scope.launch {
            _syncStatus.value = "🤖 Running AI analysis..."
            
            try {
                val result = apiClient.analyzeWithAI(
                    code = code,
                    fileName = fileName,
                    context = "User-submitted code"
                )
                
                if (result.suggestions.isNotEmpty()) {
                    val newAiSuggestions = result.suggestions.map { aiSugg ->
                        SuggestionDisplay(
                            id = "ai-manual-${System.currentTimeMillis()}-${result.suggestions.indexOf(aiSugg)}",
                            title = "🤖 ${aiSugg.title}",
                            rationale = aiSugg.rationale,
                            beforeCode = aiSugg.beforeCode,
                            afterCode = aiSugg.afterCode
                        )
                    }
                    _aiSuggestions.value = _aiSuggestions.value + newAiSuggestions
                    
                    // Also update the current UI state if we're in Ready
                    val currentState = _uiState.value
                    if (currentState is UiState.Ready) {
                        _uiState.value = currentState.copy(
                            suggestions = currentState.suggestions + newAiSuggestions
                        )
                    }
                    
                    _syncStatus.value = "✅ AI found ${result.suggestions.size} suggestion(s): ${result.summary}"
                } else {
                    _syncStatus.value = "ℹ️ AI analysis complete: ${result.summary}"
                }
            } catch (e: Exception) {
                _syncStatus.value = "❌ AI analysis failed: ${e.message}"
            }
        }
    }
    
    /**
     * Analyze a specific file with AI
     */
    fun analyzeFileWithAI(filePath: String) {
        scope.launch {
            _syncStatus.value = "🤖 Analyzing file with AI: ${filePath.substringAfterLast("/")}"
            
            try {
                val result = apiClient.analyzeFileWithAI(filePath)
                
                if (result.suggestions.isNotEmpty()) {
                    val newAiSuggestions = result.suggestions.map { aiSugg ->
                        SuggestionDisplay(
                            id = "ai-file-${System.currentTimeMillis()}-${result.suggestions.indexOf(aiSugg)}",
                            title = "🤖 ${aiSugg.title}",
                            rationale = aiSugg.rationale,
                            beforeCode = aiSugg.beforeCode,
                            afterCode = aiSugg.afterCode
                        )
                    }
                    _aiSuggestions.value = _aiSuggestions.value + newAiSuggestions
                    
                    val currentState = _uiState.value
                    if (currentState is UiState.Ready) {
                        _uiState.value = currentState.copy(
                            suggestions = currentState.suggestions + newAiSuggestions
                        )
                    }
                    
                    _syncStatus.value = "✅ AI found ${result.suggestions.size} issue(s) in file"
                } else {
                    _syncStatus.value = "ℹ️ No AI suggestions for this file"
                }
            } catch (e: Exception) {
                _syncStatus.value = "❌ AI file analysis failed: ${e.message}"
            }
        }
    }
    
    /**
     * Apply fix locally by writing file directly with backup for undo
     */
    fun applyLocalFix(suggestion: SuggestionDisplay, filePath: String) {
        scope.launch {
            _syncStatus.value = "🔧 Applying fix locally..."
            
            try {
                val afterCode = suggestion.afterCode
                if (afterCode.isNullOrEmpty()) {
                    _syncStatus.value = "❌ No fix available - missing afterCode"
                    return@launch
                }
                
                val beforeCode = suggestion.beforeCode
                if (beforeCode.isNullOrEmpty()) {
                    _syncStatus.value = "❌ No original code to compare"
                    return@launch
                }
                
                // Check if file exists and is readable
                if (!fileSystem.exists(filePath)) {
                    _syncStatus.value = "❌ File not found: $filePath"
                    return@launch
                }
                
                // Read current file content
                val currentContent = fileSystem.readFile(filePath)
                
                // Check if the beforeCode exists in the file
                if (!currentContent.contains(beforeCode)) {
                    _syncStatus.value = "⚠️ Original code pattern not found in file. May have been modified."
                    return@launch
                }
                
                // Create backup before applying fix
                undoManager.recordFix(
                    filePath = filePath,
                    originalContent = currentContent,
                    newContent = currentContent.replace(beforeCode, afterCode),
                    suggestionTitle = suggestion.title
                )
                
                // Apply the fix by replacing before code with after code
                val newContent = currentContent.replace(beforeCode, afterCode)
                fileSystem.writeFile(filePath, newContent)
                
                val fileName = fileSystem.getFileName(filePath)
                _syncStatus.value = "✅ Fix applied to $fileName (${undoManager.undoCount()} undoable)"
                
            } catch (e: Exception) {
                _syncStatus.value = "❌ Apply failed: ${e.message}"
            }
        }
    }
    
    /**
     * Apply fix with explicit file path and full replacement
     */
    fun applyFixToFile(filePath: String, newContent: String, suggestionTitle: String) {
        scope.launch {
            _syncStatus.value = "🔧 Applying fix to file..."
            
            try {
                if (!fileSystem.exists(filePath)) {
                    _syncStatus.value = "❌ File not found: $filePath"
                    return@launch
                }
                
                // Read current content for backup
                val originalContent = fileSystem.readFile(filePath)
                
                // Record for undo
                undoManager.recordFix(
                    filePath = filePath,
                    originalContent = originalContent,
                    newContent = newContent,
                    suggestionTitle = suggestionTitle
                )
                
                // Write new content
                fileSystem.writeFile(filePath, newContent)
                
                val fileName = fileSystem.getFileName(filePath)
                _syncStatus.value = "✅ Fix applied to $fileName (Undo available)"
                
            } catch (e: Exception) {
                _syncStatus.value = "❌ Apply failed: ${e.message}"
            }
        }
    }
    
    /**
     * Undo the last applied fix
     */
    fun undoLastFix() {
        scope.launch {
            val fixToUndo = undoManager.popUndo()
            
            if (fixToUndo == null) {
                _syncStatus.value = "ℹ️ Nothing to undo"
                return@launch
            }
            
            _syncStatus.value = "⏪ Undoing: ${fixToUndo.suggestionTitle}..."
            
            try {
                // Restore original content
                fileSystem.writeFile(fixToUndo.filePath, fixToUndo.originalContent)
                
                val fileName = fileSystem.getFileName(fixToUndo.filePath)
                val remaining = undoManager.undoCount()
                _syncStatus.value = "↩️ Reverted $fileName ($remaining more undoable)"
                
            } catch (e: Exception) {
                _syncStatus.value = "❌ Undo failed: ${e.message}"
            }
        }
    }
    
    /**
     * Redo a previously undone fix
     */
    fun redoLastFix() {
        scope.launch {
            val fixToRedo = undoManager.popRedo()
            
            if (fixToRedo == null) {
                _syncStatus.value = "ℹ️ Nothing to redo"
                return@launch
            }
            
            _syncStatus.value = "⏩ Redoing: ${fixToRedo.suggestionTitle}..."
            
            try {
                // Apply the new content again
                fileSystem.writeFile(fixToRedo.filePath, fixToRedo.newContent)
                
                val fileName = fileSystem.getFileName(fixToRedo.filePath)
                _syncStatus.value = "↪️ Reapplied fix to $fileName"
                
            } catch (e: Exception) {
                _syncStatus.value = "❌ Redo failed: ${e.message}"
            }
        }
    }
    
    /**
     * Undo a specific fix by ID
     */
    fun undoFix(fixId: String) {
        scope.launch {
            val history = undoManager.getRecentHistory(50)
            val fix = history.find { it.id == fixId && !it.isUndone }
            
            if (fix == null) {
                _syncStatus.value = "❌ Fix not found or already undone"
                return@launch
            }
            
            _syncStatus.value = "⏪ Undoing: ${fix.suggestionTitle}..."
            
            try {
                // Check current file content
                val currentContent = if (fileSystem.exists(fix.filePath)) {
                    fileSystem.readFile(fix.filePath)
                } else {
                    _syncStatus.value = "❌ File no longer exists: ${fix.filePath}"
                    return@launch
                }
                
                // Check if the file still has the applied changes
                if (!currentContent.contains(fix.newContent.take(100))) {
                    _syncStatus.value = "⚠️ File has been modified since fix was applied"
                }
                
                // Restore original content
                fileSystem.writeFile(fix.filePath, fix.originalContent)
                
                // Mark as undone in the manager (using popUndo for tracking)
                undoManager.clearFix(fixId)
                undoManager.recordFix(
                    filePath = fix.filePath,
                    originalContent = fix.newContent,
                    newContent = fix.originalContent,
                    suggestionTitle = "Undo: ${fix.suggestionTitle}"
                )
                
                val fileName = fileSystem.getFileName(fix.filePath)
                _syncStatus.value = "↩️ Reverted $fileName"
                
            } catch (e: Exception) {
                _syncStatus.value = "❌ Undo failed: ${e.message}"
            }
        }
    }
    
    /**
     * Get list of undoable fixes
     */
    fun getUndoHistory(): List<AppliedFix> = undoManager.getRecentHistory()
    
    /**
     * Check if undo is available
     */
    fun canUndo(): Boolean = undoManager.canUndo()
    
    /**
     * Check if redo is available
     */
    fun canRedo(): Boolean = undoManager.canRedo()
    
    /**
     * Clear all undo history
     */
    fun clearUndoHistory() {
        undoManager.clearAll()
        _syncStatus.value = "🗑️ Undo history cleared"
    }
    
    /**
     * Show diff in a more detailed format
     */
    fun getDiffDetails(suggestion: SuggestionDisplay): String {
        val before = suggestion.beforeCode ?: "No before code"
        val after = suggestion.afterCode ?: "No after code"
        
        return buildString {
            appendLine("=== BEFORE ===")
            appendLine(before)
            appendLine()
            appendLine("=== AFTER ===")
            appendLine(after)
        }
    }
    
    /**
     * Navigate to settings screen
     */
    fun navigateToSettings() {
        _uiState.value = UiState.Settings
    }
    
    /**
     * Navigate back to previous screen
     */
    fun navigateBack() {
        // For now, just go to Idle. Could be enhanced to remember previous state
        _uiState.value = UiState.Idle
    }
    
    fun saveApiConfig(groqApiKey: String, githubToken: String = "") {
        secureStorage.saveString("groqApiKey", groqApiKey)
        secureStorage.saveString("githubToken", githubToken)
        _syncStatus.value = "⚙️ API configuration saved"
        navigateBack()
    }

    /**
     * Get current API configuration (for display)
     */
    fun getApiConfig(): Map<String, String> {
        val groq = secureStorage.getString("groqApiKey") ?: ""
        val github = secureStorage.getString("githubToken") ?: ""
        return mapOf(
            "groqApiKey" to groq,
            "githubToken" to github
        )
    }
}

// Platform-specific server control hooks
expect fun startServerPlatform(viewModel: DebugForgeViewModel, groqApiKey: String, githubToken: String): Boolean
expect fun stopServerPlatform(viewModel: DebugForgeViewModel)
expect fun isServerRunningPlatform(viewModel: DebugForgeViewModel): Boolean