package com.kmpforge.debugforge.analysis

import com.kmpforge.debugforge.diagnostics.*
import com.kmpforge.debugforge.persistence.RepoIndexDao
import com.kmpforge.debugforge.utils.DebugForgeLogger
import kotlinx.datetime.Clock

/**
 * Analyzes shared KMP code for Wasm threading violations and incompatibilities.
 */
class WasmThreadSafetyAnalyzer(
    private val dao: RepoIndexDao,
    private val fileSystem: FileSystemReader
) : Analyzer {
    
    override val name: String = "WasmThreadSafetyAnalyzer"
    override val category: DiagnosticCategory = DiagnosticCategory.WASM_THREADING
    
    private val dispatcherIoPattern = Regex("""Dispatchers\s*\.\s*(IO|Default)""")
    private val threadApiPattern = Regex("""Thread\s*\(|synchronized\s*\(|@Synchronized""")
    private val atomicPattern = Regex("""AtomicInteger|AtomicLong|AtomicReference|AtomicBoolean""")
    
    override suspend fun analyze(repoPath: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        
        try {
            val modules = dao.getModulesForRepo(repoPath)
            
            // Check if any module has Wasm source sets
            var hasWasmTarget = false
            for (module in modules) {
                val sourceSets = dao.getSourceSetsForModule(module.gradlePath)
                if (sourceSets.any { it.platform.contains("wasm", ignoreCase = true) }) {
                    hasWasmTarget = true
                    break
                }
            }
            
            if (!hasWasmTarget) {
                return emptyList()
            }
            
            // Get files from commonMain source sets
            val files = dao.getFilesInSourceSets(repoPath, listOf("commonMain"))
                .filter { it.path.endsWith(".kt") }
            
            for (file in files) {
                val content = try {
                    fileSystem.readFile(file.path)
                } catch (e: Exception) {
                    continue
                }
                
                val lines = content.split("\n")
                
                lines.forEachIndexed { index, line ->
                    val lineNum = index + 1
                    
                    if (dispatcherIoPattern.containsMatchIn(line)) {
                        val fixedLine = line.replace(Regex("""Dispatchers\s*\.\s*(IO|Default)"""), "Dispatchers.Main")
                        diagnostics.add(createDiagnostic(
                            id = "wasm-dispatcher-${file.id}-$lineNum",
                            message = "Dispatchers.IO/Default not available in Wasm",
                            explanation = "Wasm is single-threaded. Use Dispatchers.Main or Dispatchers.Unconfined instead.",
                            filePath = file.path,
                            relativePath = file.relativePath,
                            moduleId = file.moduleGradlePath,
                            sourceSet = file.sourceSet,
                            line = lineNum,
                            severity = DiagnosticSeverity.ERROR,
                            codeSnippet = line.trim(),
                            fixTitle = "Use Wasm-compatible dispatcher",
                            fixText = fixedLine
                        ))
                    }
                    
                    if (threadApiPattern.containsMatchIn(line)) {
                        diagnostics.add(createDiagnostic(
                            id = "wasm-thread-${file.id}-$lineNum",
                            message = "Thread/synchronized APIs not available in Wasm",
                            explanation = "Wasm is a single-threaded environment. Thread APIs are not supported.",
                            filePath = file.path,
                            relativePath = file.relativePath,
                            moduleId = file.moduleGradlePath,
                            sourceSet = file.sourceSet,
                            line = lineNum,
                            severity = DiagnosticSeverity.ERROR,
                            codeSnippet = line.trim()
                        ))
                    }
                    
                    if (atomicPattern.containsMatchIn(line)) {
                        diagnostics.add(createDiagnostic(
                            id = "wasm-atomic-${file.id}-$lineNum",
                            message = "Atomic operations unnecessary in Wasm",
                            explanation = "Wasm is single-threaded, so atomic operations provide no benefit and may have different semantics.",
                            filePath = file.path,
                            relativePath = file.relativePath,
                            moduleId = file.moduleGradlePath,
                            sourceSet = file.sourceSet,
                            line = lineNum,
                            severity = DiagnosticSeverity.WARNING,
                            codeSnippet = line.trim()
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            DebugForgeLogger.error("WasmThreadSafetyAnalyzer", "Analysis error: ${e.message}", e)
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
                confidence = 0.85f
            ))
        } else emptyList()
        
        return Diagnostic(
            id = id,
            severity = severity,
            category = DiagnosticCategory.WASM_THREADING,
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
            source = AnalyzerSource.WASM_THREAD_SAFETY_ANALYZER,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            tags = buildSet {
                add(DiagnosticTag.CROSS_PLATFORM)
                if (fixes.isNotEmpty()) add(DiagnosticTag.FIXABLE)
            },
            fixes = fixes
        )
    }
}
