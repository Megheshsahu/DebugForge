package com.kmpforge.debugforge.export

import com.kmpforge.debugforge.core.SharedCodeMetrics
import com.kmpforge.debugforge.diagnostics.Diagnostic
import com.kmpforge.debugforge.ai.RefactorSuggestion
import kotlinx.datetime.Clock
import kotlin.math.roundToInt

/**
 * Format a floating-point number with specified decimal places.
 * Kotlin common compatible alternative to String.format.
 */
private fun formatDecimal(value: Float, decimals: Int): String {
    val factor = 10.0f.let { 
        var result = 1f
        repeat(decimals) { result *= 10f }
        result 
    }
    val rounded = ((value * factor).roundToInt() / factor)
    val intPart = rounded.toInt()
    val fracPart = ((rounded - intPart) * factor).roundToInt()
    return if (decimals > 0) "$intPart.${fracPart.toString().padStart(decimals, '0')}" else "$intPart"
}

/**
 * Generates reports about repository health and analysis results.
 */
class ReportGenerator {
    
    /**
     * Generates a Markdown report of the analysis.
     */
    fun generateMarkdownReport(
        repoName: String,
        repoPath: String,
        metrics: SharedCodeMetrics,
        diagnostics: List<Diagnostic>,
        suggestions: List<RefactorSuggestion>
    ): String {
        return buildString {
            appendLine("# DebugForge Analysis Report")
            appendLine()
            appendLine("**Repository:** $repoName")
            appendLine("**Path:** `$repoPath`")
            appendLine("**Generated:** ${Clock.System.now()}")
            appendLine()
            
            // Shared code metrics
            appendLine("## Shared Code Metrics")
            appendLine()
            appendLine("| Metric | Value |")
            appendLine("|--------|-------|")
            appendLine("| Total Lines of Code | ${metrics.totalLinesOfCode} |")
            appendLine("| Shared Lines of Code | ${metrics.sharedLinesOfCode} |")
            appendLine("| Shared Code Percentage | ${formatDecimal(metrics.sharedCodePercentage, 1)}% |")
            appendLine("| Expect Declarations | ${metrics.expectDeclarations} |")
            appendLine("| Actual Implementations | ${metrics.actualImplementations} |")
            appendLine()
            
            // Platform breakdown
            appendLine("### Platform Breakdown")
            appendLine()
            val breakdown = metrics.platformBreakdown
            appendLine("| Platform | Lines |")
            appendLine("|----------|-------|")
            appendLine("| Common | ${breakdown.commonLines} |")
            appendLine("| Android | ${breakdown.androidLines} |")
            appendLine("| iOS | ${breakdown.iosLines} |")
            appendLine("| JVM | ${breakdown.jvmLines} |")
            appendLine("| JS | ${breakdown.jsLines} |")
            appendLine("| WASM | ${breakdown.wasmLines} |")
            appendLine("| Native | ${breakdown.nativeLines} |")
            appendLine()
            
            // Diagnostics summary
            appendLine("## Diagnostics")
            appendLine()
            appendLine("Total issues found: ${diagnostics.size}")
            appendLine()
            
            val bySeverity = diagnostics.groupBy { it.severity }
            if (bySeverity.isNotEmpty()) {
                appendLine("| Severity | Count |")
                appendLine("|----------|-------|")
                bySeverity.forEach { (severity, items) ->
                    appendLine("| $severity | ${items.size} |")
                }
                appendLine()
            }
            
            // Top diagnostics
            if (diagnostics.isNotEmpty()) {
                appendLine("### Top Issues")
                appendLine()
                diagnostics.take(10).forEachIndexed { index, diagnostic ->
                    appendLine("${index + 1}. **${diagnostic.message}** (${diagnostic.severity})")
                    appendLine("   - File: `${diagnostic.location.filePath}`")
                    appendLine("   - Line: ${diagnostic.location.startLine}")
                    appendLine()
                }
            }
            
            // Refactoring suggestions
            appendLine("## Refactoring Suggestions")
            appendLine()
            appendLine("Total suggestions: ${suggestions.size}")
            appendLine()
            
            if (suggestions.isNotEmpty()) {
                suggestions.take(10).forEachIndexed { index, suggestion ->
                    appendLine("${index + 1}. **${suggestion.title}**")
                    appendLine("   - Category: ${suggestion.category}")
                    appendLine("   - Confidence: ${formatDecimal(suggestion.confidence * 100, 0)}%")
                    appendLine("   - Auto-applicable: ${if (suggestion.isAutoApplicable) "Yes" else "No"}")
                    appendLine()
                }
            }
        }
    }
    
    /**
     * Generates a JSON report of the analysis.
     */
    fun generateJsonReport(
        repoName: String,
        repoPath: String,
        metrics: SharedCodeMetrics,
        diagnostics: List<Diagnostic>,
        suggestions: List<RefactorSuggestion>
    ): String {
        // Simple JSON serialization
        return buildString {
            appendLine("{")
            appendLine("""  "repository": "$repoName",""")
            appendLine("""  "path": "$repoPath",""")
            appendLine("""  "timestamp": "${Clock.System.now()}",""")
            appendLine("""  "metrics": {""")
            appendLine("""    "totalLinesOfCode": ${metrics.totalLinesOfCode},""")
            appendLine("""    "sharedLinesOfCode": ${metrics.sharedLinesOfCode},""")
            appendLine("""    "sharedCodePercentage": ${metrics.sharedCodePercentage}""")
            appendLine("  },")
            appendLine("""  "diagnosticsCount": ${diagnostics.size},""")
            appendLine("""  "suggestionsCount": ${suggestions.size}""")
            appendLine("}")
        }
    }
}
