package com.kmpforge.debugforge.ai

import com.kmpforge.debugforge.diagnostics.*
import com.kmpforge.debugforge.analysis.FileSystemReader
import com.kmpforge.debugforge.persistence.RepoIndexDao
import kotlinx.datetime.Clock
import kotlin.math.min
import kotlin.random.Random

/**
 * AI/ML-based inference engine for complex refactoring suggestions.
 * 
 * Uses pattern-based inference with weighted scoring to identify
 * refactoring opportunities. Designed as a foundation for future
 * integration with actual ML models (Kotlin-DL ONNX).
 * 
 * The scoring system simulates ML inference by:
 * - Analyzing code patterns with feature extraction
 * - Computing weighted confidence scores
 * - Cross-referencing multiple signals for accuracy
 */
class RefactorInferenceEngine(
    private val dao: RepoIndexDao,
    private val fileSystem: FileSystemReader
) {
    // Pattern weights learned from code analysis (simulated ML weights)
    private val patternWeights = PatternWeights()
    
    // Code pattern detectors
    private val patternDetectors = listOf(
        ExpectActualPatternDetector(),
        CoroutineSafetyPatternDetector(),
        WasmCompatibilityPatternDetector(),
        CodeSharingPatternDetector(),
        ModernizationPatternDetector()
    )
    
    /**
     * Generates AI-powered refactoring suggestions.
     * Uses pattern-based inference with confidence scoring.
     */
    suspend fun generateInferenceSuggestions(
        repoPath: String,
        diagnostics: List<Diagnostic>
    ): List<RefactorSuggestion> {
        val suggestions = mutableListOf<RefactorSuggestion>()
        val timestamp = Clock.System.now().toEpochMilliseconds()
        
        // 1. Analyze diagnostics for patterns
        val diagnosticPatterns = analyzeDiagnosticPatterns(diagnostics)
        
        // 2. Extract features from code
        val codeFeatures = extractCodeFeatures(repoPath, diagnostics)
        
        // 3. Generate suggestions using pattern matching
        patternDetectors.forEach { detector ->
            val detected = detector.detect(diagnosticPatterns, codeFeatures)
            detected.forEach { pattern ->
                val confidence = computeConfidence(pattern, diagnosticPatterns, codeFeatures)
                if (confidence >= patternWeights.minConfidenceThreshold) {
                    suggestions.add(createSuggestion(pattern, confidence, timestamp))
                }
            }
        }
        
        // 4. Apply cross-file analysis for complex refactorings
        suggestions.addAll(analyzeCrossFileRefactoring(repoPath, diagnostics, timestamp))
        
        // 5. Rank and deduplicate
        return rankAndDeduplicate(suggestions)
    }
    
    /**
     * Analyzes code context for refactoring opportunities.
     */
    suspend fun analyzeContext(
        filePath: String,
        lineNumber: Int
    ): ContextAnalysis {
        val content = try {
            fileSystem.readFile(filePath)
        } catch (e: Exception) {
            return ContextAnalysis(emptyList(), emptyList())
        }
        
        val lines = content.lines()
        val contextWindow = 10
        val startLine = maxOf(0, lineNumber - contextWindow)
        val endLine = minOf(lines.size, lineNumber + contextWindow)
        
        val contextLines = lines.subList(startLine, endLine)
        val contextText = contextLines.joinToString("\n")
        
        // Extract symbols from context
        val symbols = extractSymbols(contextText)
        
        // Identify potential refactorings
        val refactorings = identifyRefactorings(contextText, filePath)
        
        return ContextAnalysis(
            symbols = symbols,
            suggestedRefactorings = refactorings
        )
    }
    
    private fun analyzeDiagnosticPatterns(diagnostics: List<Diagnostic>): DiagnosticPatternAnalysis {
        val categoryCount = diagnostics.groupingBy { it.category }.eachCount()
        val severityCount = diagnostics.groupingBy { it.severity }.eachCount()
        val sourceSetIssues = diagnostics.groupingBy { it.location.sourceSet }.eachCount()
        
        return DiagnosticPatternAnalysis(
            totalCount = diagnostics.size,
            categoryDistribution = categoryCount,
            severityDistribution = severityCount,
            sourceSetDistribution = sourceSetIssues,
            hasExpectActualIssues = categoryCount[DiagnosticCategory.EXPECT_ACTUAL] ?: 0 > 0,
            hasCoroutineIssues = categoryCount[DiagnosticCategory.COROUTINE_SAFETY] ?: 0 > 0,
            hasWasmIssues = categoryCount[DiagnosticCategory.WASM_THREADING] ?: 0 > 0,
            clusteredLocations = findClusteredDiagnostics(diagnostics)
        )
    }
    
    private fun findClusteredDiagnostics(diagnostics: List<Diagnostic>): List<DiagnosticCluster> {
        val clusters = mutableListOf<DiagnosticCluster>()
        val byFile = diagnostics.groupBy { it.location.filePath }
        
        byFile.forEach { (filePath, fileDiagnostics) ->
            if (fileDiagnostics.size >= 3) {
                // Multiple issues in same file = potential for bulk refactoring
                clusters.add(DiagnosticCluster(
                    filePath = filePath,
                    diagnosticCount = fileDiagnostics.size,
                    categories = fileDiagnostics.map { it.category }.toSet(),
                    lineRange = Pair(
                        fileDiagnostics.minOf { it.location.startLine },
                        fileDiagnostics.maxOf { it.location.endLine }
                    )
                ))
            }
        }
        
        return clusters
    }
    
    private suspend fun extractCodeFeatures(
        repoPath: String,
        diagnostics: List<Diagnostic>
    ): CodeFeatures {
        // Extract features from affected files
        val affectedFiles = diagnostics.map { it.location.filePath }.distinct()
        
        var totalExpectDeclarations = 0
        var totalActualDeclarations = 0
        var suspendFunctionCount = 0
        var platformSpecificImports = 0
        var modernApiUsage = 0
        var deprecatedApiUsage = 0
        
        affectedFiles.take(20).forEach { filePath ->
            try {
                val content = fileSystem.readFile(filePath)
                
                // Count expect/actual declarations
                totalExpectDeclarations += "\\bexpect\\s+(fun|class|object|interface|val|var)\\b".toRegex()
                    .findAll(content).count()
                totalActualDeclarations += "\\bactual\\s+(fun|class|object|interface|val|var)\\b".toRegex()
                    .findAll(content).count()
                
                // Count suspend functions
                suspendFunctionCount += "\\bsuspend\\s+fun\\b".toRegex().findAll(content).count()
                
                // Detect platform-specific imports
                if (content.contains("import java.") || content.contains("import android.")) {
                    platformSpecificImports++
                }
                if (content.contains("import platform.") || content.contains("import kotlinx.cinterop")) {
                    platformSpecificImports++
                }
                
                // Detect modern vs deprecated API usage
                if (content.contains("kotlinx.coroutines") || content.contains("Flow<")) {
                    modernApiUsage++
                }
                if (content.contains("@Deprecated") || content.contains("GlobalScope")) {
                    deprecatedApiUsage++
                }
            } catch (e: Exception) {
                // Skip unreadable files
            }
        }
        
        return CodeFeatures(
            expectDeclarationCount = totalExpectDeclarations,
            actualDeclarationCount = totalActualDeclarations,
            suspendFunctionCount = suspendFunctionCount,
            platformSpecificImports = platformSpecificImports,
            modernApiUsageScore = modernApiUsage.toFloat() / maxOf(1, affectedFiles.size),
            deprecatedApiUsageScore = deprecatedApiUsage.toFloat() / maxOf(1, affectedFiles.size),
            codeComplexityScore = computeComplexityScore(diagnostics)
        )
    }
    
    private fun computeComplexityScore(diagnostics: List<Diagnostic>): Float {
        // Complexity based on diagnostic distribution
        var severityWeight = 0
        for (diagnostic in diagnostics) {
            severityWeight += when (diagnostic.severity) {
                DiagnosticSeverity.ERROR -> 4
                DiagnosticSeverity.WARNING -> 2
                DiagnosticSeverity.INFO -> 1
                DiagnosticSeverity.HINT -> 0
            }
        }
        return min(1.0f, severityWeight / 100.0f)
    }
    
    private fun computeConfidence(
        pattern: DetectedPattern,
        diagnosticPatterns: DiagnosticPatternAnalysis,
        codeFeatures: CodeFeatures
    ): Float {
        var confidence = pattern.baseConfidence
        
        // Apply pattern-specific weight adjustments
        when (pattern.category) {
            RefactorCategory.EXPECT_ACTUAL_FIX -> {
                // Higher confidence if many expect/actual issues
                if (diagnosticPatterns.hasExpectActualIssues) {
                    confidence += 0.1f
                }
                // Boost if expect/actual declarations are unbalanced
                val ratio = codeFeatures.expectDeclarationCount.toFloat() / 
                    maxOf(1, codeFeatures.actualDeclarationCount)
                if (ratio > 1.5f || ratio < 0.67f) {
                    confidence += 0.05f
                }
            }
            RefactorCategory.COROUTINE_SAFETY -> {
                if (diagnosticPatterns.hasCoroutineIssues) {
                    confidence += 0.15f
                }
                // Higher confidence if many suspend functions
                if (codeFeatures.suspendFunctionCount > 10) {
                    confidence += 0.05f
                }
            }
            RefactorCategory.WASM_COMPATIBILITY -> {
                if (diagnosticPatterns.hasWasmIssues) {
                    confidence += 0.1f
                }
            }
            RefactorCategory.CODE_SHARING -> {
                // Higher confidence if platform-specific code detected
                if (codeFeatures.platformSpecificImports > 0) {
                    confidence += 0.1f
                }
            }
            RefactorCategory.MODERNIZATION -> {
                // Higher confidence for deprecated API usage
                if (codeFeatures.deprecatedApiUsageScore > 0.2f) {
                    confidence += 0.1f
                }
            }
            else -> {}
        }
        
        // Apply complexity adjustment
        confidence -= codeFeatures.codeComplexityScore * 0.1f
        
        // Cluster bonus: if diagnostics are clustered, easier to fix
        val relevantClusters = diagnosticPatterns.clusteredLocations.filter { cluster ->
            cluster.categories.any { categoryMatchesRefactor(it, pattern.category) }
        }
        if (relevantClusters.isNotEmpty()) {
            confidence += 0.05f
        }
        
        return confidence.coerceIn(0.0f, 1.0f)
    }
    
    private fun categoryMatchesRefactor(
        diagnosticCategory: DiagnosticCategory,
        refactorCategory: RefactorCategory
    ): Boolean {
        return when (refactorCategory) {
            RefactorCategory.EXPECT_ACTUAL_FIX -> 
                diagnosticCategory == DiagnosticCategory.EXPECT_ACTUAL
            RefactorCategory.COROUTINE_SAFETY -> 
                diagnosticCategory == DiagnosticCategory.COROUTINE_SAFETY
            RefactorCategory.WASM_COMPATIBILITY -> 
                diagnosticCategory == DiagnosticCategory.WASM_THREADING
            RefactorCategory.MODERNIZATION -> 
                diagnosticCategory == DiagnosticCategory.DEPRECATION
            else -> false
        }
    }
    
    private fun createSuggestion(
        pattern: DetectedPattern,
        confidence: Float,
        timestamp: Long
    ): RefactorSuggestion {
        return RefactorSuggestion(
            id = "ml-${pattern.id}-${Random.nextInt(10000)}",
            title = pattern.title,
            rationale = pattern.rationale,
            confidence = confidence,
            category = pattern.category,
            priority = when {
                confidence >= 0.9f -> RefactorPriority.CRITICAL
                confidence >= 0.75f -> RefactorPriority.HIGH
                confidence >= 0.5f -> RefactorPriority.MEDIUM
                else -> RefactorPriority.LOW
            },
            unifiedDiff = "",  // Generated later by DiffGenerator
            changes = emptyList(),  // Populated when applying
            affectedLocations = pattern.affectedLocations,
            fixesDiagnosticIds = pattern.diagnosticIds,
            sharedCodeImpact = pattern.estimatedSharedCodeImpact,
            isAutoApplicable = pattern.isAutoApplicable && confidence >= 0.8f,
            risks = pattern.risks,
            source = RefactorSource.ML_INFERENCE,
            generatedAt = timestamp
        )
    }
    
    private suspend fun analyzeCrossFileRefactoring(
        repoPath: String,
        diagnostics: List<Diagnostic>,
        timestamp: Long
    ): List<RefactorSuggestion> {
        val suggestions = mutableListOf<RefactorSuggestion>()
        
        // Find expect declarations missing actuals across multiple platforms
        val expectActualDiagnostics = diagnostics.filter { 
            it.category == DiagnosticCategory.EXPECT_ACTUAL 
        }
        
        if (expectActualDiagnostics.size >= 2) {
            // Group by potential related declarations
            val byModule = expectActualDiagnostics.groupBy { it.location.moduleId }
            
            byModule.forEach { (moduleId, moduleDiagnostics) ->
                if (moduleDiagnostics.size >= 2) {
                    suggestions.add(RefactorSuggestion(
                        id = "ml-cross-file-${moduleId.hashCode()}",
                        title = "Consolidate expect/actual declarations in $moduleId",
                        rationale = "Multiple expect/actual issues detected in module '$moduleId'. " +
                            "Consider consolidating related declarations or using interface extraction " +
                            "to reduce platform-specific code.",
                        confidence = 0.75f,
                        category = RefactorCategory.CODE_SHARING,
                        priority = RefactorPriority.MEDIUM,
                        unifiedDiff = "",
                        changes = emptyList(),
                        affectedLocations = moduleDiagnostics.map { it.location },
                        fixesDiagnosticIds = moduleDiagnostics.map { it.id },
                        sharedCodeImpact = estimateSharedCodeImpact(moduleDiagnostics.size),
                        isAutoApplicable = false,
                        risks = listOf(
                            RefactorRisk(
                                type = RiskType.BREAKING_CHANGE,
                                description = "Cross-file refactoring may affect public API",
                                severity = RiskSeverity.MEDIUM
                            )
                        ),
                        source = RefactorSource.ML_INFERENCE,
                        generatedAt = timestamp
                    ))
                }
            }
        }
        
        // Detect opportunities to extract shared interfaces
        val commonPatternFiles = findCommonPatternFiles(diagnostics)
        if (commonPatternFiles.isNotEmpty()) {
            suggestions.add(RefactorSuggestion(
                id = "ml-interface-extraction-${timestamp}",
                title = "Extract shared interface from platform implementations",
                rationale = "Detected similar patterns across platform-specific files. " +
                    "Extracting a common interface to commonMain would increase shared code percentage.",
                confidence = 0.7f,
                category = RefactorCategory.CODE_SHARING,
                priority = RefactorPriority.HIGH,
                unifiedDiff = "",
                changes = emptyList(),
                affectedLocations = commonPatternFiles.flatMap { it.locations },
                fixesDiagnosticIds = emptyList(),
                sharedCodeImpact = 0.05f, // Estimated 5% increase
                isAutoApplicable = false,
                risks = listOf(
                    RefactorRisk(
                        type = RiskType.BEHAVIOR_CHANGE,
                        description = "Interface extraction may require behavioral alignment",
                        severity = RiskSeverity.LOW
                    )
                ),
                source = RefactorSource.ML_INFERENCE,
                generatedAt = timestamp
            ))
        }
        
        return suggestions
    }
    
    private fun findCommonPatternFiles(diagnostics: List<Diagnostic>): List<CommonPatternGroup> {
        // Group diagnostics by message similarity
        val groups = mutableListOf<CommonPatternGroup>()
        val byMessage = diagnostics.groupBy { 
            it.message.take(50) // Group by first 50 chars of message
        }
        
        byMessage.forEach { (messagePrefix, groupDiagnostics) ->
            if (groupDiagnostics.size >= 3) {
                groups.add(CommonPatternGroup(
                    pattern = messagePrefix,
                    locations = groupDiagnostics.map { it.location }
                ))
            }
        }
        
        return groups
    }
    
    private fun estimateSharedCodeImpact(issueCount: Int): Float {
        // Rough estimate: each fixed issue could contribute to shared code
        return min(0.1f, issueCount * 0.01f)
    }
    
    private fun rankAndDeduplicate(suggestions: List<RefactorSuggestion>): List<RefactorSuggestion> {
        // Remove duplicates based on affected locations
        val seen = mutableSetOf<String>()
        return suggestions
            .sortedByDescending { it.confidence }
            .filter { suggestion ->
                val key = suggestion.affectedLocations
                    .sortedBy { it.filePath + it.startLine }
                    .joinToString("|") { "${it.filePath}:${it.startLine}" }
                if (seen.contains(key)) {
                    false
                } else {
                    seen.add(key)
                    true
                }
            }
    }
    
    private fun extractSymbols(code: String): List<String> {
        val symbols = mutableListOf<String>()
        
        // Extract function names
        "\\bfun\\s+(\\w+)".toRegex().findAll(code).forEach {
            symbols.add(it.groupValues[1])
        }
        
        // Extract class names
        "\\b(class|interface|object)\\s+(\\w+)".toRegex().findAll(code).forEach {
            symbols.add(it.groupValues[2])
        }
        
        // Extract property names
        "\\b(val|var)\\s+(\\w+)".toRegex().findAll(code).forEach {
            symbols.add(it.groupValues[2])
        }
        
        return symbols.distinct()
    }
    
    private fun identifyRefactorings(code: String, filePath: String): List<String> {
        val refactorings = mutableListOf<String>()
        
        if (code.contains("GlobalScope")) {
            refactorings.add("Replace GlobalScope with structured concurrency")
        }
        
        if (code.contains("runBlocking")) {
            refactorings.add("Consider replacing runBlocking with suspend function")
        }
        
        if ("\\bvar\\s+\\w+\\s*=".toRegex().containsMatchIn(code)) {
            refactorings.add("Consider using val instead of var where possible")
        }
        
        if (code.contains("!!")) {
            refactorings.add("Replace !! with safe null handling")
        }
        
        if (code.contains("import java.") && filePath.contains("commonMain")) {
            refactorings.add("Replace Java imports with Kotlin stdlib equivalents")
        }
        
        if (code.contains("Thread.sleep")) {
            refactorings.add("Replace Thread.sleep with delay for coroutines")
        }
        
        if ("catch\\s*\\(\\s*e\\s*:\\s*Exception\\s*\\)".toRegex().containsMatchIn(code)) {
            refactorings.add("Consider catching specific exceptions")
        }
        
        return refactorings
    }
}

/**
 * Result of context analysis.
 */
data class ContextAnalysis(
    val symbols: List<String>,
    val suggestedRefactorings: List<String>
)

/**
 * Pattern weights for ML-like scoring.
 * These could be loaded from a trained model file.
 */
data class PatternWeights(
    val expectActualWeight: Float = 0.85f,
    val coroutineWeight: Float = 0.8f,
    val wasmWeight: Float = 0.75f,
    val codeSharingWeight: Float = 0.7f,
    val modernizationWeight: Float = 0.65f,
    val minConfidenceThreshold: Float = 0.5f
)

/**
 * Analysis of diagnostic patterns.
 */
data class DiagnosticPatternAnalysis(
    val totalCount: Int,
    val categoryDistribution: Map<DiagnosticCategory, Int>,
    val severityDistribution: Map<DiagnosticSeverity, Int>,
    val sourceSetDistribution: Map<String, Int>,
    val hasExpectActualIssues: Boolean,
    val hasCoroutineIssues: Boolean,
    val hasWasmIssues: Boolean,
    val clusteredLocations: List<DiagnosticCluster>
)

/**
 * A cluster of diagnostics in the same file.
 */
data class DiagnosticCluster(
    val filePath: String,
    val diagnosticCount: Int,
    val categories: Set<DiagnosticCategory>,
    val lineRange: Pair<Int, Int>
)

/**
 * Extracted code features for inference.
 */
data class CodeFeatures(
    val expectDeclarationCount: Int,
    val actualDeclarationCount: Int,
    val suspendFunctionCount: Int,
    val platformSpecificImports: Int,
    val modernApiUsageScore: Float,
    val deprecatedApiUsageScore: Float,
    val codeComplexityScore: Float
)

/**
 * A detected refactoring pattern.
 */
data class DetectedPattern(
    val id: String,
    val title: String,
    val rationale: String,
    val category: RefactorCategory,
    val baseConfidence: Float,
    val affectedLocations: List<DiagnosticLocation>,
    val diagnosticIds: List<String>,
    val estimatedSharedCodeImpact: Float?,
    val isAutoApplicable: Boolean,
    val risks: List<RefactorRisk>
)

/**
 * Group of files with common patterns.
 */
data class CommonPatternGroup(
    val pattern: String,
    val locations: List<DiagnosticLocation>
)

/**
 * Base interface for pattern detectors.
 */
interface PatternDetector {
    fun detect(
        diagnosticPatterns: DiagnosticPatternAnalysis,
        codeFeatures: CodeFeatures
    ): List<DetectedPattern>
}

/**
 * Detects expect/actual mismatch patterns.
 */
class ExpectActualPatternDetector : PatternDetector {
    override fun detect(
        diagnosticPatterns: DiagnosticPatternAnalysis,
        codeFeatures: CodeFeatures
    ): List<DetectedPattern> {
        val patterns = mutableListOf<DetectedPattern>()
        
        if (diagnosticPatterns.hasExpectActualIssues) {
            val count = diagnosticPatterns.categoryDistribution[DiagnosticCategory.EXPECT_ACTUAL] ?: 0
            
            patterns.add(DetectedPattern(
                id = "expect-actual-consolidation",
                title = "Consolidate expect/actual declarations",
                rationale = "Found $count expect/actual issues. Consider consolidating " +
                    "declarations or using type aliases to reduce platform-specific code.",
                category = RefactorCategory.EXPECT_ACTUAL_FIX,
                baseConfidence = 0.8f,
                affectedLocations = emptyList(),
                diagnosticIds = emptyList(),
                estimatedSharedCodeImpact = estimateImpact(count),
                isAutoApplicable = false,
                risks = listOf(
                    RefactorRisk(
                        type = RiskType.BREAKING_CHANGE,
                        description = "May require updates to platform-specific code",
                        severity = RiskSeverity.MEDIUM
                    )
                )
            ))
        }
        
        return patterns
    }
    
    private fun estimateImpact(issueCount: Int): Float {
        return min(0.15f, issueCount * 0.02f)
    }
}

/**
 * Detects coroutine safety patterns.
 */
class CoroutineSafetyPatternDetector : PatternDetector {
    override fun detect(
        diagnosticPatterns: DiagnosticPatternAnalysis,
        codeFeatures: CodeFeatures
    ): List<DetectedPattern> {
        val patterns = mutableListOf<DetectedPattern>()
        
        if (diagnosticPatterns.hasCoroutineIssues) {
            patterns.add(DetectedPattern(
                id = "coroutine-scope-fix",
                title = "Fix coroutine scope issues",
                rationale = "Detected coroutine safety issues. Consider using structured " +
                    "concurrency patterns like coroutineScope or supervisorScope.",
                category = RefactorCategory.COROUTINE_SAFETY,
                baseConfidence = 0.85f,
                affectedLocations = emptyList(),
                diagnosticIds = emptyList(),
                estimatedSharedCodeImpact = null,
                isAutoApplicable = true,
                risks = listOf(
                    RefactorRisk(
                        type = RiskType.BEHAVIOR_CHANGE,
                        description = "May change exception propagation behavior",
                        severity = RiskSeverity.LOW
                    )
                )
            ))
        }
        
        if (codeFeatures.suspendFunctionCount > 20 && 
            codeFeatures.deprecatedApiUsageScore > 0.1f) {
            patterns.add(DetectedPattern(
                id = "coroutine-modernization",
                title = "Modernize coroutine usage",
                rationale = "Large codebase with coroutines detected. Consider updating " +
                    "to latest coroutines patterns like StateFlow and SharedFlow.",
                category = RefactorCategory.MODERNIZATION,
                baseConfidence = 0.7f,
                affectedLocations = emptyList(),
                diagnosticIds = emptyList(),
                estimatedSharedCodeImpact = null,
                isAutoApplicable = false,
                risks = emptyList()
            ))
        }
        
        return patterns
    }
}

/**
 * Detects Wasm compatibility patterns.
 */
class WasmCompatibilityPatternDetector : PatternDetector {
    override fun detect(
        diagnosticPatterns: DiagnosticPatternAnalysis,
        codeFeatures: CodeFeatures
    ): List<DetectedPattern> {
        val patterns = mutableListOf<DetectedPattern>()
        
        if (diagnosticPatterns.hasWasmIssues) {
            patterns.add(DetectedPattern(
                id = "wasm-thread-fix",
                title = "Fix Wasm threading issues",
                rationale = "Wasm target requires single-threaded execution. Replace " +
                    "multi-threaded patterns with Wasm-safe alternatives.",
                category = RefactorCategory.WASM_COMPATIBILITY,
                baseConfidence = 0.9f,
                affectedLocations = emptyList(),
                diagnosticIds = emptyList(),
                estimatedSharedCodeImpact = null,
                isAutoApplicable = false,
                risks = listOf(
                    RefactorRisk(
                        type = RiskType.PERFORMANCE_IMPACT,
                        description = "Single-threaded alternatives may be slower",
                        severity = RiskSeverity.MEDIUM
                    )
                )
            ))
        }
        
        return patterns
    }
}

/**
 * Detects code sharing opportunities.
 */
class CodeSharingPatternDetector : PatternDetector {
    override fun detect(
        diagnosticPatterns: DiagnosticPatternAnalysis,
        codeFeatures: CodeFeatures
    ): List<DetectedPattern> {
        val patterns = mutableListOf<DetectedPattern>()
        
        if (codeFeatures.platformSpecificImports > 3) {
            patterns.add(DetectedPattern(
                id = "platform-abstraction",
                title = "Extract platform abstractions",
                rationale = "Multiple platform-specific imports detected. Create expect/actual " +
                    "abstractions to improve code sharing across platforms.",
                category = RefactorCategory.CODE_SHARING,
                baseConfidence = 0.75f,
                affectedLocations = emptyList(),
                diagnosticIds = emptyList(),
                estimatedSharedCodeImpact = 0.05f,
                isAutoApplicable = false,
                risks = emptyList()
            ))
        }
        
        // Check expect/actual balance
        val expectCount = codeFeatures.expectDeclarationCount
        val actualCount = codeFeatures.actualDeclarationCount
        if (expectCount > 0 && actualCount > expectCount * 3) {
            patterns.add(DetectedPattern(
                id = "reduce-actual-implementations",
                title = "Reduce actual implementations",
                rationale = "More actual than expect declarations detected. Consider " +
                    "using default implementations or abstract classes to reduce duplication.",
                category = RefactorCategory.CODE_SHARING,
                baseConfidence = 0.7f,
                affectedLocations = emptyList(),
                diagnosticIds = emptyList(),
                estimatedSharedCodeImpact = 0.03f,
                isAutoApplicable = false,
                risks = listOf(
                    RefactorRisk(
                        type = RiskType.BEHAVIOR_CHANGE,
                        description = "Default implementations may not suit all platforms",
                        severity = RiskSeverity.LOW
                    )
                )
            ))
        }
        
        return patterns
    }
}

/**
 * Detects modernization opportunities.
 */
class ModernizationPatternDetector : PatternDetector {
    override fun detect(
        diagnosticPatterns: DiagnosticPatternAnalysis,
        codeFeatures: CodeFeatures
    ): List<DetectedPattern> {
        val patterns = mutableListOf<DetectedPattern>()
        
        if (codeFeatures.deprecatedApiUsageScore > 0.2f) {
            patterns.add(DetectedPattern(
                id = "deprecation-cleanup",
                title = "Replace deprecated API usage",
                rationale = "High deprecated API usage detected. Update to modern " +
                    "equivalents for better maintainability and performance.",
                category = RefactorCategory.MODERNIZATION,
                baseConfidence = 0.8f,
                affectedLocations = emptyList(),
                diagnosticIds = emptyList(),
                estimatedSharedCodeImpact = null,
                isAutoApplicable = true,
                risks = listOf(
                    RefactorRisk(
                        type = RiskType.BEHAVIOR_CHANGE,
                        description = "New APIs may have slightly different behavior",
                        severity = RiskSeverity.LOW
                    )
                )
            ))
        }
        
        if (codeFeatures.modernApiUsageScore < 0.3f && 
            codeFeatures.suspendFunctionCount > 5) {
            patterns.add(DetectedPattern(
                id = "kotlin-idioms",
                title = "Apply Kotlin idioms",
                rationale = "Code could benefit from modern Kotlin idioms like " +
                    "scope functions, extension functions, and sealed classes.",
                category = RefactorCategory.MODERNIZATION,
                baseConfidence = 0.65f,
                affectedLocations = emptyList(),
                diagnosticIds = emptyList(),
                estimatedSharedCodeImpact = null,
                isAutoApplicable = false,
                risks = emptyList()
            ))
        }
        
        return patterns
    }
}
