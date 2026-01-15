package com.kmpforge.debugforge.analysis

import com.kmpforge.debugforge.diagnostics.*
import com.kmpforge.debugforge.persistence.RepoIndexDao
import com.kmpforge.debugforge.utils.DebugForgeLogger
import kotlinx.datetime.Clock

/**
 * Analyzes expect/actual declarations for mismatches and missing implementations.
 */
class ExpectActualAnalyzer(
    private val dao: RepoIndexDao
) : Analyzer {
    
    override val name: String = "ExpectActualAnalyzer"
    override val category: DiagnosticCategory = DiagnosticCategory.EXPECT_ACTUAL
    
    override suspend fun analyze(repoPath: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        
        try {
            val missingActuals = dao.getMissingActuals()
            
            for (missing in missingActuals) {
                diagnostics.add(Diagnostic(
                    id = "expect-actual-${missing.mapping.id ?: Clock.System.now().toEpochMilliseconds()}",
                    severity = DiagnosticSeverity.ERROR,
                    category = DiagnosticCategory.EXPECT_ACTUAL,
                    message = "Missing actual implementation for '${missing.expectName}'",
                    explanation = "The expect declaration '${missing.expectQualifiedName}' does not have a corresponding actual implementation for platform: ${missing.mapping.actualPlatform ?: "unknown"}",
                    location = DiagnosticLocation(
                        filePath = missing.expectFilePath,
                        relativeFilePath = missing.expectFilePath.substringAfterLast("/"),
                        moduleId = "shared",
                        sourceSet = "commonMain",
                        startLine = 1,
                        startColumn = 1,
                        endLine = 1,
                        endColumn = 1
                    ),
                    source = AnalyzerSource.EXPECT_ACTUAL_ANALYZER,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                    tags = setOf(DiagnosticTag.FIXABLE, DiagnosticTag.CROSS_PLATFORM),
                    fixes = listOf(
                        DiagnosticFix(
                            title = "Generate actual implementation",
                            description = "Create actual implementation for ${missing.expectQualifiedName}",
                            edits = listOf(
                                TextEdit(
                                    filePath = "${missing.mapping.actualPlatform ?: "jvm"}Main/${missing.expectName}.kt",
                                    range = TextRange(1, 1, 1, 1),
                                    newText = "actual class ${missing.expectName} {\n    // TODO: Implement\n}\n"
                                )
                            ),
                            isPreferred = true,
                            confidence = 0.85f
                        )
                    )
                ))
            }
            
            // Check for signature mismatches
            val allMappings = dao.getExpectActualMappings()
            for (mapping in allMappings.filter { it.mismatchReason != null }) {
                diagnostics.add(Diagnostic(
                    id = "expect-actual-mismatch-${mapping.id ?: Clock.System.now().toEpochMilliseconds()}",
                    severity = DiagnosticSeverity.WARNING,
                    category = DiagnosticCategory.EXPECT_ACTUAL,
                    message = "Signature mismatch in expect/actual",
                    explanation = mapping.mismatchReason ?: "Signature does not match",
                    location = DiagnosticLocation(
                        filePath = "",
                        relativeFilePath = "",
                        moduleId = "shared",
                        sourceSet = "commonMain",
                        startLine = 1,
                        startColumn = 1,
                        endLine = 1,
                        endColumn = 1
                    ),
                    source = AnalyzerSource.EXPECT_ACTUAL_ANALYZER,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                    tags = setOf(DiagnosticTag.CROSS_PLATFORM)
                ))
            }
        } catch (e: Exception) {
            DebugForgeLogger.error("ExpectActualAnalyzer", "Analysis error: ${e.message}", e)
        }
        
        return diagnostics
    }
}
