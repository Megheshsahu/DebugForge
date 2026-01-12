package com.kmpforge.debugforge.analysis

import com.kmpforge.debugforge.diagnostics.*
import com.kmpforge.debugforge.persistence.RepoIndexDao
import kotlinx.datetime.Clock

/**
 * Detects coroutine-related issues in shared KMP code through static analysis.
 */
class CoroutineLeakDetector(
    private val dao: RepoIndexDao,
    private val fileSystem: FileSystemReader
) : Analyzer {
    
    override val name: String = "CoroutineLeakDetector"
    override val category: DiagnosticCategory = DiagnosticCategory.COROUTINE_SAFETY
    
    private val globalScopePattern = Regex("""GlobalScope\s*\.\s*(launch|async)""")
    private val blockingCallPattern = Regex("""Thread\.sleep|runBlocking\s*\{""")
    
    override suspend fun analyze(repoPath: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        
        try {
            // Get all modules and then their files
            val modules = dao.getModulesForRepo(repoPath)
            val allFiles = mutableListOf<com.kmpforge.debugforge.persistence.IndexedFileEntity>()
            
            for (module in modules) {
                val moduleFiles = dao.getFilesInModule(module.gradlePath)
                allFiles.addAll(moduleFiles.filter { it.path.endsWith(".kt") })
            }
            
            for (file in allFiles) {
                val content = try {
                    fileSystem.readFile(file.path)
                } catch (e: Exception) {
                    continue
                }
                
                val lines = content.split("\n")
                
                lines.forEachIndexed { index, line ->
                    val lineNum = index + 1
                    
                    if (globalScopePattern.containsMatchIn(line)) {
                        val fixedLine = line.replace("GlobalScope", "scope")
                        diagnostics.add(createDiagnostic(
                            id = "coroutine-globalscope-${file.id}-$lineNum",
                            message = "GlobalScope usage detected - may cause coroutine leaks",
                            explanation = "GlobalScope is not tied to any lifecycle. Consider using viewModelScope, lifecycleScope, or a custom CoroutineScope.",
                            filePath = file.path,
                            relativePath = file.relativePath,
                            moduleId = file.moduleGradlePath,
                            sourceSet = file.sourceSet,
                            line = lineNum,
                            severity = DiagnosticSeverity.WARNING,
                            codeSnippet = line.trim(),
                            fixTitle = "Replace with structured concurrency",
                            fixText = fixedLine
                        ))
                    }
                    
                    if (blockingCallPattern.containsMatchIn(line)) {
                        val fixedLine = line.replace("Thread.sleep", "delay")
                        diagnostics.add(createDiagnostic(
                            id = "coroutine-blocking-${file.id}-$lineNum",
                            message = "Blocking call detected in coroutine context",
                            explanation = "Use delay() instead of Thread.sleep() in coroutines to avoid blocking the thread.",
                            filePath = file.path,
                            relativePath = file.relativePath,
                            moduleId = file.moduleGradlePath,
                            sourceSet = file.sourceSet,
                            line = lineNum,
                            severity = DiagnosticSeverity.ERROR,
                            codeSnippet = line.trim(),
                            fixTitle = "Replace with non-blocking delay()",
                            fixText = fixedLine
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            println("CoroutineLeakDetector error: ${e.message}")
        }
        
        return diagnostics
    }
    
    private fun createDiagnostic(
        id: String,
        message: String,
        explanation: String,
        filePath: String,
        relativePath: String,
        moduleId: String,
        sourceSet: String,
        line: Int,
        severity: DiagnosticSeverity,
        codeSnippet: String? = null,
        fixTitle: String? = null,
        fixText: String? = null
    ): Diagnostic {
        val fixes = if (fixTitle != null && fixText != null) {
            listOf(DiagnosticFix(
                title = fixTitle,
                description = "Automatically fix this issue",
                edits = listOf(TextEdit(
                    filePath = filePath,
                    range = TextRange(line, 1, line, 1000),
                    newText = fixText
                )),
                isPreferred = true,
                confidence = 0.8f
            ))
        } else emptyList()
        
        return Diagnostic(
            id = id,
            severity = severity,
            category = DiagnosticCategory.COROUTINE_SAFETY,
            message = message,
            explanation = explanation,
            codeSnippet = codeSnippet,
            location = DiagnosticLocation(
                filePath = filePath,
                relativeFilePath = relativePath,
                moduleId = moduleId,
                sourceSet = sourceSet,
                startLine = line,
                startColumn = 1,
                endLine = line,
                endColumn = 1000
            ),
            source = AnalyzerSource.COROUTINE_LEAK_DETECTOR,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            tags = if (fixes.isNotEmpty()) setOf(DiagnosticTag.FIXABLE) else emptySet(),
            fixes = fixes
        )
    }
}
