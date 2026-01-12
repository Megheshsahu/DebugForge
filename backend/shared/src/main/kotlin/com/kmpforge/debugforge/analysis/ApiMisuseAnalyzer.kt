package com.kmpforge.debugforge.analysis

import com.kmpforge.debugforge.diagnostics.*
import com.kmpforge.debugforge.persistence.RepoIndexDao
import kotlinx.datetime.Clock

/**
 * Detects common API misuse patterns in KMP code.
 */
class ApiMisuseAnalyzer(
    private val dao: RepoIndexDao,
    private val fileSystem: FileSystemReader
) : Analyzer {
    
    override val name: String = "ApiMisuseAnalyzer"
    override val category: DiagnosticCategory = DiagnosticCategory.API_MISUSE
    
    private val androidImportPattern = Regex("""import\s+android\.""")
    private val iosImportPattern = Regex("""import\s+platform\.Foundation\.|import\s+platform\.UIKit\.""")
    private val jvmImportPattern = Regex("""import\s+java\.|import\s+javax\.""")
    
    private val resourcePatterns = listOf(
        Regex("""\.openStream\(\)|FileInputStream\(|FileOutputStream\(""") to "Stream opened without use/close pattern",
        Regex("""HttpClient\s*\(""") to "HttpClient created - ensure it's closed after use"
    )
    
    override suspend fun analyze(repoPath: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        
        try {
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
                    
                    if (androidImportPattern.containsMatchIn(line)) {
                        diagnostics.add(createDiagnostic(
                            id = "api-android-import-${file.id}-$lineNum",
                            message = "Android-specific import in common code",
                            explanation = "This will fail on non-Android platforms. Use expect/actual pattern instead.",
                            filePath = file.path,
                            relativePath = file.relativePath,
                            moduleId = file.moduleGradlePath,
                            sourceSet = file.sourceSet,
                            line = lineNum,
                            severity = DiagnosticSeverity.ERROR,
                            codeSnippet = line.trim()
                        ))
                    }
                    
                    if (jvmImportPattern.containsMatchIn(line)) {
                        diagnostics.add(createDiagnostic(
                            id = "api-jvm-import-${file.id}-$lineNum",
                            message = "JVM-specific import in common code",
                            explanation = "Use expect/actual or multiplatform alternatives instead of java.*/javax.* imports.",
                            filePath = file.path,
                            relativePath = file.relativePath,
                            moduleId = file.moduleGradlePath,
                            sourceSet = file.sourceSet,
                            line = lineNum,
                            severity = DiagnosticSeverity.ERROR,
                            codeSnippet = line.trim()
                        ))
                    }
                    
                    if (iosImportPattern.containsMatchIn(line)) {
                        diagnostics.add(createDiagnostic(
                            id = "api-ios-import-${file.id}-$lineNum",
                            message = "iOS-specific import in common code",
                            explanation = "This will fail on non-iOS platforms. Use expect/actual pattern instead.",
                            filePath = file.path,
                            relativePath = file.relativePath,
                            moduleId = file.moduleGradlePath,
                            sourceSet = file.sourceSet,
                            line = lineNum,
                            severity = DiagnosticSeverity.ERROR,
                            codeSnippet = line.trim()
                        ))
                    }
                    
                    for ((pattern, message) in resourcePatterns) {
                        if (pattern.containsMatchIn(line)) {
                            diagnostics.add(createDiagnostic(
                                id = "api-resource-${file.id}-$lineNum",
                                message = message,
                                explanation = "Resources should be properly closed to avoid leaks. Consider using .use {} pattern.",
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
            }
        } catch (e: Exception) {
            println("ApiMisuseAnalyzer error: ${e.message}")
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
        codeSnippet: String? = null
    ): Diagnostic {
        return Diagnostic(
            id = id,
            severity = severity,
            category = DiagnosticCategory.API_MISUSE,
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
            source = AnalyzerSource.API_MISUSE_ANALYZER,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            tags = setOf(DiagnosticTag.CROSS_PLATFORM)
        )
    }
}
