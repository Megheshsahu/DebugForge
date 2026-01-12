package com.kmpforge.debugforge.core

import com.kmpforge.debugforge.persistence.RepoIndexDao

/**
 * Analyzes a KMP repository to calculate shared code metrics.
 * Determines how much code is truly shared vs platform-specific.
 */
class SharedCodeAnalyzer(
    private val dao: RepoIndexDao
) {
    /**
     * Analyzes the repository and calculates shared code metrics.
     */
    suspend fun analyze(repoPath: String): SharedCodeMetrics {
        val modules = dao.getModulesForRepo(repoPath)
        
        var totalLines = 0
        var commonLines = 0
        var androidLines = 0
        var iosLines = 0
        var jvmLines = 0
        var jsLines = 0
        var wasmLines = 0
        var nativeLines = 0
        
        val moduleRankings = mutableListOf<ModuleSharedRanking>()
        var rank = 1
        
        for (module in modules) {
            val sourceSets = dao.getSourceSetsForModule(module.gradlePath)
            
            var moduleTotal = 0
            var moduleCommon = 0
            
            for (sourceSet in sourceSets) {
                moduleTotal += sourceSet.lineCount
                when {
                    sourceSet.name.contains("common", ignoreCase = true) -> {
                        commonLines += sourceSet.lineCount
                        moduleCommon += sourceSet.lineCount
                    }
                    sourceSet.name.contains("android", ignoreCase = true) -> 
                        androidLines += sourceSet.lineCount
                    sourceSet.name.contains("ios", ignoreCase = true) -> 
                        iosLines += sourceSet.lineCount
                    sourceSet.name.contains("jvm", ignoreCase = true) || 
                    sourceSet.name.contains("desktop", ignoreCase = true) -> 
                        jvmLines += sourceSet.lineCount
                    sourceSet.name.contains("js", ignoreCase = true) -> 
                        jsLines += sourceSet.lineCount
                    sourceSet.name.contains("wasm", ignoreCase = true) -> 
                        wasmLines += sourceSet.lineCount
                    sourceSet.name.contains("native", ignoreCase = true) ||
                    sourceSet.name.contains("linux", ignoreCase = true) ||
                    sourceSet.name.contains("macos", ignoreCase = true) ||
                    sourceSet.name.contains("mingw", ignoreCase = true) -> 
                        nativeLines += sourceSet.lineCount
                }
            }
            
            totalLines += moduleTotal
            
            if (moduleTotal > 0) {
                moduleRankings.add(ModuleSharedRanking(
                    moduleId = module.gradlePath,
                    moduleName = module.displayName,
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
        
        // Count expect/actual declarations
        val expectSymbols = dao.getExpectSymbols()
        val actualSymbols = dao.getActualSymbols()
        val expectCount = expectSymbols.size
        val actualCount = actualSymbols.size
        val expectActualCoverage = if (expectCount > 0) {
            (actualCount.toFloat() / expectCount).coerceAtMost(1f)
        } else 0f
        
        return SharedCodeMetrics(
            totalLinesOfCode = totalLines,
            sharedLinesOfCode = commonLines,
            sharedCodePercentage = sharedPercentage,
            platformBreakdown = PlatformCodeBreakdown(
                commonLines = commonLines,
                androidLines = androidLines,
                iosLines = iosLines,
                jvmLines = jvmLines,
                jsLines = jsLines,
                wasmLines = wasmLines,
                nativeLines = nativeLines
            ),
            expectDeclarations = expectCount,
            actualImplementations = actualCount,
            expectActualCoverage = expectActualCoverage,
            moduleRankings = moduleRankings.sortedByDescending { it.sharedPercentage },
            sharableCandidates = emptyList() // Would need more sophisticated analysis
        )
    }
    
    /**
     * Finds files that could potentially be shared across platforms.
     */
    suspend fun findSharableCandidates(repoPath: String): List<SharableCandidate> {
        // Placeholder - would analyze platform-specific files for sharing potential
        return emptyList()
    }
}
