package com.kmpforge.debugforge.ai

import com.kmpforge.debugforge.diagnostics.*
import com.kmpforge.debugforge.analysis.FileSystemReader
import com.kmpforge.debugforge.persistence.RepoIndexDao
import kotlinx.datetime.Clock

/**
 * Rule-based refactoring engine that generates refactor suggestions.
 * 
 * Stub implementation that returns empty suggestions.
 * TODO: Implement full rule-based refactoring logic.
 */
class RefactorRuleEngine(
    private val dao: RepoIndexDao,
    private val fileSystem: FileSystemReader
) {
    /**
     * Generates refactor suggestions based on diagnostics.
     * Each diagnostic with fixes generates a refactor suggestion.
     */
    suspend fun generateFromDiagnostics(
        diagnostics: List<Diagnostic>
    ): List<RefactorSuggestion> {
        // Stub implementation - returns empty list
        return diagnostics
            .filter { it.fixes.isNotEmpty() }
            .mapIndexed { index, diagnostic ->
                createSuggestionFromDiagnostic(diagnostic, index)
            }
    }
    
    private fun createSuggestionFromDiagnostic(
        diagnostic: Diagnostic,
        index: Int
    ): RefactorSuggestion {
        val fix = diagnostic.fixes.firstOrNull()
        val timestamp = Clock.System.now().toEpochMilliseconds()
        
        // Extract before/after code from diagnostic context and fix
        val beforeCode = diagnostic.codeSnippet ?: ""
        val afterCode = fix?.edits?.firstOrNull()?.newText ?: ""
        
        // Build unified diff from before/after
        val unifiedDiff = if (beforeCode.isNotEmpty() || afterCode.isNotEmpty()) {
            buildUnifiedDiff(beforeCode, afterCode, diagnostic.location.filePath)
        } else {
            ""
        }
        
        // Build file changes from edits
        val changes = if (fix != null && fix.edits.isNotEmpty()) {
            listOf(
                FileChange(
                    filePath = diagnostic.location.filePath,
                    changeType = ChangeType.MODIFIED,
                    hunks = listOf(
                        DiffHunk(
                            originalStart = diagnostic.location.startLine,
                            originalCount = beforeCode.lines().size,
                            modifiedStart = diagnostic.location.startLine,
                            modifiedCount = afterCode.lines().size,
                            lines = buildDiffLines(beforeCode, afterCode)
                        )
                    )
                )
            )
        } else {
            emptyList()
        }
        
        return RefactorSuggestion(
            id = "rule-${diagnostic.id}-$index",
            title = fix?.title ?: "Fix: ${diagnostic.message}",
            rationale = "This refactoring addresses: ${diagnostic.message}",
            confidence = fix?.confidence ?: 0.8f,
            category = mapCategoryToRefactorCategory(diagnostic.category),
            priority = mapSeverityToPriority(diagnostic.severity),
            unifiedDiff = unifiedDiff,
            changes = changes,
            affectedLocations = listOf(diagnostic.location),
            fixesDiagnosticIds = listOf(diagnostic.id),
            sharedCodeImpact = null,
            isAutoApplicable = fix?.isPreferred ?: false,
            risks = emptyList(),
            source = RefactorSource.RULE_ENGINE,
            generatedAt = timestamp
        )
    }
    
    private fun buildUnifiedDiff(before: String, after: String, filePath: String): String {
        val sb = StringBuilder()
        sb.appendLine("--- a/$filePath")
        sb.appendLine("+++ b/$filePath")
        
        before.lines().forEach { line ->
            sb.appendLine("-$line")
        }
        after.lines().forEach { line ->
            sb.appendLine("+$line")
        }
        
        return sb.toString()
    }
    
    private fun buildDiffLines(before: String, after: String): List<DiffLine> {
        val lines = mutableListOf<DiffLine>()
        var lineNum = 1
        
        before.lines().forEach { line ->
            lines.add(DiffLine(
                type = DiffLineType.DELETION,
                content = line,
                originalLineNumber = lineNum++,
                modifiedLineNumber = null
            ))
        }
        
        lineNum = 1
        after.lines().forEach { line ->
            lines.add(DiffLine(
                type = DiffLineType.ADDITION,
                content = line,
                originalLineNumber = null,
                modifiedLineNumber = lineNum++
            ))
        }
        
        return lines
    }
    
    private fun mapCategoryToRefactorCategory(category: DiagnosticCategory): RefactorCategory {
        return when (category) {
            DiagnosticCategory.EXPECT_ACTUAL -> RefactorCategory.EXPECT_ACTUAL_FIX
            DiagnosticCategory.COROUTINE_SAFETY -> RefactorCategory.COROUTINE_SAFETY
            DiagnosticCategory.WASM_THREADING -> RefactorCategory.WASM_COMPATIBILITY
            DiagnosticCategory.API_MISUSE -> RefactorCategory.MODERNIZATION
            else -> RefactorCategory.CLEANUP
        }
    }
    
    private fun mapSeverityToPriority(severity: DiagnosticSeverity): RefactorPriority {
        return when (severity) {
            DiagnosticSeverity.ERROR -> RefactorPriority.CRITICAL
            DiagnosticSeverity.WARNING -> RefactorPriority.HIGH
            DiagnosticSeverity.INFO -> RefactorPriority.MEDIUM
            DiagnosticSeverity.HINT -> RefactorPriority.LOW
        }
    }
    
    /**
     * Analyzes repository and generates proactive refactor suggestions.
     * These are suggestions not tied to specific diagnostics.
     */
    suspend fun analyzeForRefactoring(repoPath: String): List<RefactorSuggestion> {
        val suggestions = mutableListOf<RefactorSuggestion>()
        val timestamp = Clock.System.now().toEpochMilliseconds()
        
        // Apply rule-based analysis to find refactoring opportunities
        suggestions.addAll(analyzeCodePatterns(repoPath, timestamp))
        
        return suggestions
    }
    
    /**
     * Analyzes code patterns in the repository.
     */
    private suspend fun analyzeCodePatterns(
        repoPath: String,
        timestamp: Long
    ): List<RefactorSuggestion> {
        val suggestions = mutableListOf<RefactorSuggestion>()
        
        // Get modules for this repo
        val modules = dao.getModulesForRepo(repoPath)
        
        // Analyze files from each module
        for (module in modules) {
            val files = dao.getFilesInModule(module.gradlePath)
            for (file in files) {
                try {
                    val content = fileSystem.readFile(file.path)
                    suggestions.addAll(analyzeFilePatterns(file.path, file.sourceSet, content, timestamp))
                } catch (e: Exception) {
                    // Skip unreadable files
                }
            }
        }
        
        return suggestions.take(50) // Limit suggestions
    }
    
    /**
     * Analyzes a single file for refactoring patterns.
     */
    private fun analyzeFilePatterns(
        filePath: String,
        sourceSet: String,
        content: String,
        timestamp: Long
    ): List<RefactorSuggestion> {
        val suggestions = mutableListOf<RefactorSuggestion>()
        // Rule 1: GlobalScope usage
        if (content.contains("GlobalScope")) {
            suggestions.add(createPatternSuggestion(
                id = "rule-globalscope-${filePath.hashCode()}",
                title = "Replace GlobalScope with structured concurrency",
                rationale = "GlobalScope is discouraged as it doesn't respect lifecycle. " +
                    "Use CoroutineScope tied to a lifecycle or viewModelScope.",
                category = RefactorCategory.COROUTINE_SAFETY,
                priority = RefactorPriority.HIGH,
                filePath = filePath,
                sourceSet = sourceSet,
                timestamp = timestamp,
                fileContent = content
            ))
        }
        // Rule 2: runBlocking in non-test code
        if (content.contains("runBlocking") && !filePath.contains("Test")) {
            suggestions.add(createPatternSuggestion(
                id = "rule-runblocking-${filePath.hashCode()}",
                title = "Consider replacing runBlocking",
                rationale = "runBlocking blocks the current thread. Consider using " +
                    "suspend functions or launching coroutines from a scope.",
                category = RefactorCategory.COROUTINE_SAFETY,
                priority = RefactorPriority.MEDIUM,
                filePath = filePath,
                sourceSet = sourceSet,
                timestamp = timestamp,
                fileContent = content
            ))
        }
        // Rule 3: Force unwrap (!!) usage
        val forceUnwrapCount = "!!".toRegex().findAll(content).count()
        if (forceUnwrapCount > 0) {
            suggestions.add(createPatternSuggestion(
                id = "rule-forceunwrap-${filePath.hashCode()}",
                title = "Replace force unwrap (!!) with safe alternatives",
                rationale = "Found $forceUnwrapCount force unwrap operators. Consider using " +
                    "?.let, ?:, or requireNotNull() for safer null handling.",
                category = RefactorCategory.CLEANUP,
                priority = RefactorPriority.LOW,
                filePath = filePath,
                sourceSet = sourceSet,
                timestamp = timestamp,
                fileContent = content
            ))
        }
        // Rule 4: Java imports in commonMain
        if (sourceSet == "commonMain" && 
            (content.contains("import java.") || content.contains("import javax."))) {
            suggestions.add(createPatternSuggestion(
                id = "rule-javaimport-${filePath.hashCode()}",
                title = "Replace Java imports with Kotlin alternatives",
                rationale = "Java imports in commonMain prevent code sharing. Use " +
                    "Kotlin stdlib equivalents or create expect/actual abstractions.",
                category = RefactorCategory.CODE_SHARING,
                priority = RefactorPriority.HIGH,
                filePath = filePath,
                sourceSet = sourceSet,
                timestamp = timestamp,
                fileContent = content
            ))
        }
        // Rule 5: Thread.sleep usage
        if (content.contains("Thread.sleep")) {
            suggestions.add(createPatternSuggestion(
                id = "rule-threadsleep-${filePath.hashCode()}",
                title = "Replace Thread.sleep with delay",
                rationale = "Thread.sleep blocks the thread and isn't multiplatform-compatible. " +
                    "Use kotlinx.coroutines.delay() instead.",
                category = RefactorCategory.WASM_COMPATIBILITY,
                priority = RefactorPriority.HIGH,
                filePath = filePath,
                sourceSet = sourceSet,
                timestamp = timestamp,
                fileContent = content
            ))
        }
        // Rule 6: Large classes (many functions)
        val functionCount = "\\bfun\\s+".toRegex().findAll(content).count()
        if (functionCount > 20) {
            suggestions.add(createPatternSuggestion(
                id = "rule-largeclass-${filePath.hashCode()}",
                title = "Consider splitting large class",
                rationale = "File contains $functionCount functions. Consider extracting " +
                    "related functionality into separate classes for better maintainability.",
                category = RefactorCategory.CLEANUP,
                priority = RefactorPriority.LOW,
                filePath = filePath,
                sourceSet = sourceSet,
                timestamp = timestamp,
                fileContent = content
            ))
        }
        // Rule 7: Deprecated annotation usage
        if ("@Deprecated".toRegex().findAll(content).count() > 0 &&
            !"ReplaceWith".toRegex().containsMatchIn(content)) {
            suggestions.add(createPatternSuggestion(
                id = "rule-deprecated-${filePath.hashCode()}",
                title = "Add ReplaceWith to @Deprecated annotations",
                rationale = "Deprecated elements should include ReplaceWith annotations " +
                    "to help users migrate to alternatives.",
                category = RefactorCategory.MODERNIZATION,
                priority = RefactorPriority.LOW,
                filePath = filePath,
                sourceSet = sourceSet,
                timestamp = timestamp,
                fileContent = content
            ))
        }
        return suggestions
    }
    
    private fun createPatternSuggestion(
        id: String,
        title: String,
        rationale: String,
        category: RefactorCategory,
        priority: RefactorPriority,
        filePath: String,
        sourceSet: String,
        timestamp: Long,
        fileContent: String? = null
    ): RefactorSuggestion {

        var beforeCode: String? = null
        var afterCode: String? = null
        var diffGenerated = false

        if (fileContent != null) {
            when {
                title.contains("GlobalScope") && fileContent.contains("GlobalScope") -> {
                    beforeCode = fileContent
                    afterCode = fileContent.replace("GlobalScope", "scope")
                    diffGenerated = true
                }
                title.contains("Thread.sleep") && fileContent.contains("Thread.sleep") -> {
                    beforeCode = fileContent
                    afterCode = fileContent.replace("Thread.sleep", "delay")
                    diffGenerated = true
                }
                title.contains("force unwrap (!!)", ignoreCase = true) && fileContent.contains("!!") -> {
                    // Only show the lines with !! for beforeCode, and the replaced lines for afterCode
                    val lines = fileContent.lines()
                    val beforeLines = lines.filter { it.contains("!!") }
                    val afterLines = beforeLines.map { it.replace("!!", "?: error(\"Null value\")") }
                    beforeCode = beforeLines.joinToString("\n")
                    afterCode = afterLines.joinToString("\n")
                    diffGenerated = beforeLines.isNotEmpty() && afterLines.isNotEmpty()
                }
            }
        }

        val unifiedDiff = if (diffGenerated && beforeCode != null && afterCode != null) {
            buildUnifiedDiff(beforeCode, afterCode, filePath)
        } else ""

        val changes = if (diffGenerated && beforeCode != null && afterCode != null) {
            listOf(
                FileChange(
                    filePath = filePath,
                    changeType = ChangeType.MODIFIED,
                    hunks = listOf(
                        DiffHunk(
                            originalStart = 1,
                            originalCount = beforeCode.lines().size,
                            modifiedStart = 1,
                            modifiedCount = afterCode.lines().size,
                            lines = buildDiffLines(beforeCode, afterCode)
                        )
                    )
                )
            )
        } else emptyList()

        return RefactorSuggestion(
            id = id,
            title = title,
            rationale = rationale,
            confidence = when (priority) {
                RefactorPriority.CRITICAL -> 0.95f
                RefactorPriority.HIGH -> 0.85f
                RefactorPriority.MEDIUM -> 0.75f
                RefactorPriority.LOW -> 0.6f
            },
            category = category,
            priority = priority,
            unifiedDiff = unifiedDiff,
            changes = changes,
            affectedLocations = listOf(
                DiagnosticLocation(
                    filePath = filePath,
                    relativeFilePath = filePath.substringAfterLast("/"),
                    moduleId = "main",
                    sourceSet = sourceSet,
                    startLine = 1,
                    startColumn = 1,
                    endLine = 1,
                    endColumn = 1,
                    sourceSnippet = null
                )
            ),
            fixesDiagnosticIds = emptyList(),
            sharedCodeImpact = if (category == RefactorCategory.CODE_SHARING) 0.02f else null,
            isAutoApplicable = diffGenerated,
            risks = emptyList(),
            source = RefactorSource.RULE_ENGINE,
            generatedAt = timestamp
        )
    }
}
