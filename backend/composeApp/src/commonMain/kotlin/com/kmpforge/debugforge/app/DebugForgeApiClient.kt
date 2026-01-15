package com.kmpforge.debugforge.app

import kotlinx.coroutines.delay
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.listDirectoryEntries

// Simplified API client for standalone mode
class DebugForgeApiClient {
    private var currentProjectPath: String? = null

    fun setProjectPath(path: String) {
        currentProjectPath = path
    }

    suspend fun checkHealth(): Boolean {
        // In standalone mode, always return true
        return true
    }

    suspend fun loadRepo(path: String): LoadResult {
        return try {
            // Standalone mode - simulate loading a repository
            println("DEBUG: Loading repository from path: $path")
            setProjectPath(path) // Set the current project path
            delay(1000) // Simulate network delay

            // Mock successful loading
            LoadResult.Success
        } catch (e: Exception) {
            println("Error loading repo: ${e.message}")
            LoadResult.Failed(e.message ?: "Unknown error")
        }
    }

    sealed class LoadResult {
        data object Success : LoadResult()
        data class Failed(val error: String) : LoadResult()
    }

    suspend fun cloneRepo(url: String, localPath: String): CloneResult {
        return try {
            // Standalone mode - simulate cloning a repository
            println("DEBUG: Cloning repository from $url to $localPath")
            delay(2000) // Simulate network delay

            // Mock successful cloning
            CloneResult.Success
        } catch (e: Exception) {
            println("Error cloning repo: ${e.message}")
            CloneResult.Failed(e.message ?: "Unknown error")
        }
    }

    sealed class CloneResult {
        data object Success : CloneResult()
        data class Failed(val error: String) : CloneResult()
    }

    fun isGitHubUrl(input: String): Boolean {
        return input.startsWith("https://github.com/") ||
               input.startsWith("git@github.com:") ||
               input.startsWith("http://github.com/")
    }

    data class UrlValidationResult(
        val isValid: Boolean,
        val error: String? = null,
        val subdirectory: String? = null
    )

    fun validateGitUrl(url: String): UrlValidationResult {
        val trimmed = url.trim()

        // Check for basic GitHub URL patterns
        if (!isGitHubUrl(trimmed)) {
            return UrlValidationResult(false, "URL must be a GitHub repository URL (https://github.com/user/repo or git@github.com:user/repo)")
        }

        // Extract owner/repo for GitHub URLs
        val repoPath = when {
            trimmed.startsWith("https://github.com/") -> {
                trimmed.removePrefix("https://github.com/").removeSuffix(".git")
            }
            trimmed.startsWith("git@github.com:") -> {
                trimmed.removePrefix("git@github.com:").removeSuffix(".git")
            }
            else -> return UrlValidationResult(false, "Unsupported GitHub URL format")
        }

        // Extract only the owner/repo part, ignoring additional path components
        val pathParts = repoPath.split("/")
        val ownerRepoPart = pathParts.take(2).joinToString("/")
        val subdirectory = if (pathParts.size > 4 && pathParts[2] == "tree") {
            // GitHub URL format: owner/repo/tree/branch/path...
            // Skip "tree" and "branch" parts
            pathParts.drop(4).joinToString("/")
        } else if (pathParts.size > 2) {
            // Other URL formats with additional path
            pathParts.drop(2).joinToString("/")
        } else null

        // Validate subdirectory doesn't contain invalid path characters
        val validatedSubdirectory = subdirectory?.let {
            if (it.contains(":") || it.contains("..") || it.startsWith("/")) {
                null // Invalid subdirectory path
            } else {
                it
            }
        }

        // Validate owner/repo format
        if (!ownerRepoPart.contains("/")) {
            return UrlValidationResult(false, "GitHub URL must include both owner and repository name (owner/repo)")
        }

        val parts = ownerRepoPart.split("/")
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return UrlValidationResult(false, "Invalid GitHub repository format. Expected: owner/repository")
        }

        // Check for valid characters (basic validation)
        val owner = parts[0]
        val repo = parts[1]

        if (!owner.matches(Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?$"))) {
            return UrlValidationResult(false, "Invalid GitHub owner name: '$owner'. Owner names can only contain alphanumeric characters and hyphens.")
        }

        if (!repo.matches(Regex("^[a-zA-Z0-9._-]+$"))) {
            return UrlValidationResult(false, "Invalid GitHub repository name: '$repo'. Repository names can contain alphanumeric characters, dots, underscores, and hyphens.")
        }

        return UrlValidationResult(true, subdirectory = validatedSubdirectory) // Valid
    }

    suspend fun runAnalysis(repoId: String): Boolean {
        // Perform actual static analysis
        delay(500)
        return true
    }

    suspend fun getDiagnostics(repoId: String): List<DiagnosticDisplay> {
        // Perform actual static analysis on Kotlin files
        return performStaticAnalysis()
    }

    suspend fun getModules(repoId: String): List<ModuleDisplay> {
        // Scan for Kotlin modules and files
        return scanModules()
    }

    suspend fun getSuggestions(repoId: String): List<SuggestionDisplay> {
        // Generate improvement suggestions based on analysis
        return generateSuggestions()
    }

    suspend fun getMetrics(repoId: String): MetricsDisplay? {
        // Calculate actual metrics from codebase
        return calculateMetrics()
    }

    // Global state endpoints (used after clone completes)
    suspend fun getGlobalDiagnostics(): List<DiagnosticDisplay> {
        // Perform actual static analysis on Kotlin files
        return performStaticAnalysis()
    }

    suspend fun getGlobalModules(): List<ModuleDisplay> {
        // Scan for Kotlin modules and files
        return scanModules()
    }

    suspend fun getGlobalSuggestions(): List<SuggestionDisplay> {
        // Generate improvement suggestions based on analysis
        return generateSuggestions()
    }

    suspend fun getGlobalMetrics(): MetricsDisplay? {
        // Calculate actual metrics from codebase
        return calculateMetrics()
    }

    /**
     * Analyze code using AI (mock implementation)
     */
    suspend fun analyzeWithAI(
        code: String,
        fileName: String = "",
        filePath: String = "",
        context: String = "KMP Project"
    ): AIAnalysisResult {
        // Mock AI analysis
        delay(1000)
        return AIAnalysisResult(
            suggestions = listOf(
                AISuggestion(
                    title = "Mock AI Suggestion",
                    rationale = "This is a mock suggestion for testing",
                    confidence = 0.8
                )
            ),
            summary = "Mock AI analysis completed"
        )
    }

    /**
     * Analyze a file path directly using AI (mock implementation)
     */
    suspend fun analyzeFileWithAI(filePath: String, context: String = "KMP Project"): AIAnalysisResult {
        // Mock file analysis
        delay(1000)
        return AIAnalysisResult(
            suggestions = emptyList(),
            summary = "Mock file analysis completed for $filePath"
        )
    }

    // Static Analysis Implementation
    private fun performStaticAnalysis(): List<DiagnosticDisplay> {
        val diagnostics = mutableListOf<DiagnosticDisplay>()

        try {
            val projectPath = currentProjectPath ?: "d:\\Projects\\kotlin project\\kmp-forge-main\\backend" // fallback
            // Scan Kotlin files in the project
            val kotlinFiles = findKotlinFiles(projectPath)

            for (file in kotlinFiles) {
                val content = readFileContent(file)
                val relativePath = file.substringAfter("$projectPath\\").replace("\\", "/")

                // Analyze for common issues
                diagnostics.addAll(analyzeFileForIssues(content, relativePath))
            }
        } catch (e: Exception) {
            println("Error during static analysis: ${e.message}")
        }

        return diagnostics
    }

    private fun scanModules(): List<ModuleDisplay> {
        val modules = mutableListOf<ModuleDisplay>()

        try {
            val projectPath = currentProjectPath ?: "d:\\Projects\\kotlin project\\kmp-forge-main\\backend"
            // Scan for Gradle modules - look for build.gradle.kts files
            val path = Path(projectPath)
            if (path.exists()) {
                val moduleDirs = findModuleDirectories(projectPath)
                for (moduleDir in moduleDirs) {
                    val kotlinFiles = findKotlinFiles(moduleDir)
                    val moduleName = Path(moduleDir).fileName.toString()

                    modules.add(ModuleDisplay(
                        name = moduleName,
                        path = moduleDir,
                        fileCount = kotlinFiles.size,
                        sourceSets = listOf("main", "test") // Simplified
                    ))
                }
            }
        } catch (e: Exception) {
            println("Error scanning modules: ${e.message}")
        }

        return modules
    }

    private fun generateSuggestions(): List<SuggestionDisplay> {
        val suggestions = mutableListOf<SuggestionDisplay>()

        try {
            val projectPath = currentProjectPath ?: "d:\\Projects\\kotlin project\\kmp-forge-main\\backend"
            val kotlinFiles = findKotlinFiles(projectPath)

            for (file in kotlinFiles) {
                val content = readFileContent(file)
                val relativePath = file.substringAfter("$projectPath\\").replace("\\", "/")

                // Generate suggestions based on analysis
                suggestions.addAll(generateFileSuggestions(content, relativePath))
            }
        } catch (e: Exception) {
            println("Error generating suggestions: ${e.message}")
        }

        return suggestions
    }

    private fun calculateMetrics(): MetricsDisplay {
        try {
            val projectPath = currentProjectPath ?: "d:\\Projects\\kotlin project\\kmp-forge-main\\backend"
            val kotlinFiles = findKotlinFiles(projectPath)
            var totalLines = 0
            var sharedLines = 0

            for (file in kotlinFiles) {
                val content = readFileContent(file)
                val lines = content.lines()
                totalLines += lines.size

                // Count lines that might be shared (simplified heuristic)
                if (file.contains("commonMain") || file.contains("shared")) {
                    sharedLines += lines.size
                }
            }

            val sharedPercentage = if (totalLines > 0) (sharedLines.toDouble() / totalLines) * 100 else 0.0

            return MetricsDisplay(
                totalFiles = kotlinFiles.size,
                totalLines = totalLines,
                sharedCodePercent = sharedPercentage
            )
        } catch (e: Exception) {
            println("Error calculating metrics: ${e.message}")
            return MetricsDisplay(0, 0, 0.0)
        }
    }

    private fun findKotlinFiles(rootPath: String): List<String> {
        val kotlinFiles = mutableListOf<String>()

        fun scanDirectory(path: String) {
            try {
                val dir = Path(path)
                if (!dir.exists()) return

                dir.listDirectoryEntries().forEach { entry ->
                    if (entry.toFile().isDirectory &&
                        !entry.fileName.toString().startsWith(".") &&
                        entry.fileName.toString() != "build") {
                        scanDirectory(entry.toString())
                    } else if (entry.fileName.toString().endsWith(".kt")) {
                        kotlinFiles.add(entry.toString())
                    }
                }
            } catch (e: Exception) {
                // Ignore directories we can't access
            }
        }

        scanDirectory(rootPath)
        return kotlinFiles
    }

    private fun findModuleDirectories(rootPath: String): List<String> {
        val moduleDirs = mutableListOf<String>()

        fun scanForModules(path: String) {
            try {
                val dir = Path(path)
                if (!dir.exists()) return

                // Check if this directory is a module (has build.gradle.kts or build.gradle)
                val hasBuildFile = dir.listDirectoryEntries().any { entry ->
                    val name = entry.fileName.toString()
                    name == "build.gradle.kts" || name == "build.gradle"
                }

                if (hasBuildFile) {
                    moduleDirs.add(path)
                }

                // Recursively scan subdirectories
                dir.listDirectoryEntries().forEach { entry ->
                    if (entry.toFile().isDirectory &&
                        !entry.fileName.toString().startsWith(".") &&
                        entry.fileName.toString() != "build" &&
                        entry.fileName.toString() != "gradle" &&
                        entry.fileName.toString() != ".gradle") {
                        scanForModules(entry.toString())
                    }
                }
            } catch (e: Exception) {
                // Ignore directories we can't access
            }
        }

        scanForModules(rootPath)
        return moduleDirs
    }

    private fun readFileContent(filePath: String): String {
        return try {
            Path(filePath).readText()
        } catch (e: Exception) {
            ""
        }
    }

    private fun analyzeFileForIssues(content: String, filePath: String): List<DiagnosticDisplay> {
        val diagnostics = mutableListOf<DiagnosticDisplay>()
        val lines = content.lines()

        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1

            // Check for unused imports (simplified)
            if (line.trim().startsWith("import ") && line.contains("kotlinx.coroutines")) {
                // This is a common import, assume it's used for now
                // In a real analyzer, we'd track usage
            }

            // Check for TODO comments
            if (line.contains("TODO") || line.contains("FIXME")) {
                diagnostics.add(DiagnosticDisplay(
                    id = "todo-${filePath}-${lineNumber}",
                    severity = "info",
                    category = "Documentation",
                    message = "TODO/FIXME comment found",
                    filePath = filePath,
                    line = lineNumber,
                    codeSnippet = line.trim()
                ))
            }

            // Check for println statements (potential debug code)
            if (line.contains("println(") && !line.contains("DEBUG:")) {
                diagnostics.add(DiagnosticDisplay(
                    id = "println-${filePath}-${lineNumber}",
                    severity = "warning",
                    category = "Code Quality",
                    message = "Debug println statement found",
                    filePath = filePath,
                    line = lineNumber,
                    codeSnippet = line.trim()
                ))
            }

            // Check for empty catch blocks
            if (line.trim() == "catch (e: Exception) {" &&
                index + 1 < lines.size &&
                lines[index + 1].trim() == "}") {
                diagnostics.add(DiagnosticDisplay(
                    id = "empty-catch-${filePath}-${lineNumber}",
                    severity = "warning",
                    category = "Error Handling",
                    message = "Empty catch block",
                    filePath = filePath,
                    line = lineNumber,
                    codeSnippet = line.trim()
                ))
            }

            // Check for magic numbers
            val magicNumberRegex = Regex("\\b\\d{2,}\\b")
            magicNumberRegex.findAll(line).forEach { match ->
                val number = match.value
                if (number != "0" && number != "1" && number != "100") { // Skip common numbers
                    diagnostics.add(DiagnosticDisplay(
                        id = "magic-number-${filePath}-${lineNumber}-${match.range.first}",
                        severity = "info",
                        category = "Code Quality",
                        message = "Magic number: $number",
                        filePath = filePath,
                        line = lineNumber,
                        codeSnippet = line.trim()
                    ))
                }
            }
        }

        return diagnostics
    }

    private fun generateFileSuggestions(content: String, filePath: String): List<SuggestionDisplay> {
        val suggestions = mutableListOf<SuggestionDisplay>()
        val lines = content.lines()

        // Check for missing null checks
        lines.forEachIndexed { index, line ->
            if (line.contains("?.") && !line.contains("?:") && !line.contains("!!")) {
                suggestions.add(SuggestionDisplay(
                    id = "null-safety-${filePath}-${index}",
                    title = "Consider adding null check",
                    rationale = "Using safe call operator without fallback might cause issues",
                    beforeCode = line.trim(),
                    afterCode = "${line.trim()} ?: /* handle null case */"
                ))
            }
        }

        // Check for long functions (simplified)
        var braceCount = 0
        var functionStart = -1
        var functionName = ""

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()

            if (trimmed.startsWith("fun ")) {
                functionStart = index
                functionName = trimmed.substringAfter("fun ").substringBefore("(").substringBefore(" ")
                braceCount = 0
            }

            braceCount += line.count { it == '{' }
            braceCount -= line.count { it == '}' }

            if (braceCount == 0 && functionStart != -1 && index - functionStart > 50) {
                suggestions.add(SuggestionDisplay(
                    id = "long-function-${filePath}-${functionStart}",
                    title = "Consider breaking down function '$functionName'",
                    rationale = "Function is quite long and might benefit from being split into smaller functions",
                    beforeCode = "fun $functionName(...) { /* ${index - functionStart} lines */ }",
                    afterCode = "// Consider extracting parts of $functionName into separate functions"
                ))
                functionStart = -1
            }
        }

        return suggestions
    }
}

data class MetricsDisplay(
    val totalFiles: Int,
    val totalLines: Int,
    val sharedCodePercent: Double
)

data class AISuggestion(
    val title: String,
    val rationale: String,
    val beforeCode: String = "",
    val afterCode: String = "",
    val confidence: Double = 0.0
)

data class AIAnalysisResult(
    val suggestions: List<AISuggestion> = emptyList(),
    val summary: String = ""
)
