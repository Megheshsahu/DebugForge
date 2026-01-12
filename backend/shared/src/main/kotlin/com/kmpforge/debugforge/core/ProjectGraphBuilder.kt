package com.kmpforge.debugforge.core

import com.kmpforge.debugforge.persistence.RepoIndexDao
import kotlinx.serialization.Serializable

/**
 * Builds a dependency graph of the project modules and symbols.
 * 
 * This class creates a comprehensive view of:
 * 1. Module dependencies (inter-module relationships)
 * 2. Symbol dependencies (class/function usage across modules)
 * 3. Platform dependency chains
 */
class ProjectGraphBuilder(
    private val dao: RepoIndexDao
) {
    /**
     * Builds a complete project dependency graph.
     */
    suspend fun buildGraph(repoPath: String): ProjectGraph {
        val modules = dao.getModulesForRepo(repoPath)
        
        val moduleNodes = mutableListOf<ModuleNode>()
        val moduleEdges = mutableListOf<ModuleEdge>()
        val symbolNodes = mutableListOf<SymbolNode>()
        val symbolEdges = mutableListOf<SymbolEdge>()
        
        // Build module nodes
        for (module in modules) {
            val sourceSets = dao.getSourceSetsForModule(module.gradlePath)
            val fileCount = dao.getFileCount(module.gradlePath)
            val lineCount = dao.getTotalLineCount(module.gradlePath) ?: 0
            
            moduleNodes.add(ModuleNode(
                id = module.gradlePath,
                name = module.displayName,
                path = module.absolutePath,
                isKmp = module.isKmpModule,
                hasCommonMain = module.hasCommonMain,
                platforms = sourceSets.map { it.platform }.distinct(),
                fileCount = fileCount.toInt(),
                lineCount = lineCount.toInt(),
                metrics = ModuleMetrics(
                    complexity = calculateModuleComplexity(module.gradlePath),
                    maintainability = calculateMaintainability(module.gradlePath),
                    testability = calculateTestability(module.gradlePath)
                )
            ))
        }
        
        // Build module edges (dependencies between modules)
        for (module in modules) {
            val files = dao.getFilesInModule(module.gradlePath)
            val importedModules = mutableSetOf<String>()
            
            for (file in files) {
                val references = dao.getReferencesInFile(file.id!!)
                for (ref in references) {
                    // Find which module contains the referenced symbol
                    val symbol = dao.getSymbol(ref.reference.symbolId)
                    if (symbol != null) {
                        val symbolFile = dao.getFileById(symbol.fileId)
                        if (symbolFile != null && symbolFile.moduleGradlePath != module.gradlePath) {
                            importedModules.add(symbolFile.moduleGradlePath)
                        }
                    }
                }
            }
            
            for (targetModule in importedModules) {
                moduleEdges.add(ModuleEdge(
                    sourceId = module.gradlePath,
                    targetId = targetModule,
                    dependencyType = DependencyType.IMPLEMENTATION
                ))
            }
        }
        
        // Build symbol nodes (classes, interfaces, important functions)
        val classSymbols = dao.getClassSymbols()
        for (symbol in classSymbols) {
            val file = dao.getFileById(symbol.fileId) ?: continue
            
            symbolNodes.add(SymbolNode(
                id = symbol.id.toString(),
                name = symbol.name,
                qualifiedName = symbol.qualifiedName,
                kind = SymbolKind.valueOf(symbol.kind.uppercase()),
                moduleId = file.moduleGradlePath,
                sourceSet = file.sourceSet,
                isExpect = symbol.isExpect,
                isActual = symbol.isActual,
                visibility = Visibility.valueOf(symbol.visibility.uppercase()),
                metrics = SymbolMetrics(
                    referenceCount = dao.getReferencesToSymbol(symbol.id!!).size,
                    complexity = estimateSymbolComplexity(symbol),
                    cohesion = 0f // Would need more analysis
                )
            ))
        }
        
        // Build symbol edges (usage relationships)
        for (symbol in classSymbols) {
            val references = dao.getReferencesToSymbol(symbol.id!!)
            
            for (ref in references) {
                val refFile = dao.getFileById(ref.reference.referencingFileId)
                if (refFile != null) {
                    // Find the containing symbol (if any)
                    val containingSymbols = dao.getSymbolsInFile(refFile.id!!)
                    val containingSymbol = containingSymbols.find { s ->
                        ref.reference.referenceLine >= s.startLine && 
                        ref.reference.referenceLine <= s.endLine
                    }
                    
                    if (containingSymbol != null && containingSymbol.id != symbol.id) {
                        symbolEdges.add(SymbolEdge(
                            sourceId = containingSymbol.id.toString(),
                            targetId = symbol.id.toString(),
                            referenceType = ReferenceType.valueOf(
                                ref.reference.referenceType.uppercase()
                            ),
                            line = ref.reference.referenceLine
                        ))
                    }
                }
            }
        }
        
        // Calculate graph-level metrics
        val graphMetrics = calculateGraphMetrics(moduleNodes, moduleEdges, symbolNodes, symbolEdges)
        
        return ProjectGraph(
            moduleNodes = moduleNodes,
            moduleEdges = moduleEdges,
            symbolNodes = symbolNodes,
            symbolEdges = symbolEdges,
            metrics = graphMetrics
        )
    }
    
    /**
     * Finds circular dependencies in the module graph.
     */
    suspend fun findCircularDependencies(repoPath: String): List<CircularDependency> {
        val graph = buildGraph(repoPath)
        val circular = mutableListOf<CircularDependency>()
        
        // Build adjacency list
        val adjacency = mutableMapOf<String, MutableList<String>>()
        for (edge in graph.moduleEdges) {
            adjacency.getOrPut(edge.sourceId) { mutableListOf() }.add(edge.targetId)
        }
        
        // DFS for cycle detection
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val path = mutableListOf<String>()
        
        fun dfs(node: String): Boolean {
            visited.add(node)
            recursionStack.add(node)
            path.add(node)
            
            for (neighbor in adjacency[node] ?: emptyList()) {
                if (!visited.contains(neighbor)) {
                    if (dfs(neighbor)) return true
                } else if (recursionStack.contains(neighbor)) {
                    // Found cycle
                    val cycleStart = path.indexOf(neighbor)
                    val cycle = path.subList(cycleStart, path.size).toList() + neighbor
                    circular.add(CircularDependency(
                        modules = cycle,
                        severity = if (cycle.size <= 2) {
                            CircularSeverity.HIGH
                        } else {
                            CircularSeverity.MEDIUM
                        }
                    ))
                    return false // Continue to find more cycles
                }
            }
            
            path.removeAt(path.lastIndex)
            recursionStack.remove(node)
            return false
        }
        
        for (node in graph.moduleNodes.map { it.id }) {
            if (!visited.contains(node)) {
                dfs(node)
            }
        }
        
        return circular.distinctBy { it.modules.sorted() }
    }
    
    /**
     * Finds unused dependencies in the module graph.
     */
    suspend fun findUnusedDependencies(repoPath: String): List<UnusedDependency> {
        // This would require parsing build files to know declared dependencies
        // vs. the actual usage we detect from symbol references
        // For now, return empty - would need build file parsing
        return emptyList()
    }
    
    /**
     * Calculates the critical path in the build graph.
     */
    suspend fun findCriticalBuildPath(repoPath: String): List<String> {
        val graph = buildGraph(repoPath)
        
        // Topological sort with path tracking
        val inDegree = mutableMapOf<String, Int>()
        val longestPath = mutableMapOf<String, Int>()
        val predecessor = mutableMapOf<String, String?>()
        
        for (node in graph.moduleNodes) {
            inDegree[node.id] = 0
            longestPath[node.id] = 0
            predecessor[node.id] = null
        }
        
        for (edge in graph.moduleEdges) {
            inDegree[edge.targetId] = (inDegree[edge.targetId] ?: 0) + 1
        }
        
        // Find all source nodes (no incoming edges)
        val queue = ArrayDeque<String>()
        for ((node, degree) in inDegree) {
            if (degree == 0) {
                queue.add(node)
                longestPath[node] = 1
            }
        }
        
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            
            for (edge in graph.moduleEdges.filter { it.sourceId == current }) {
                val newPath = (longestPath[current] ?: 0) + 1
                if (newPath > (longestPath[edge.targetId] ?: 0)) {
                    longestPath[edge.targetId] = newPath
                    predecessor[edge.targetId] = current
                }
                
                inDegree[edge.targetId] = (inDegree[edge.targetId] ?: 1) - 1
                if (inDegree[edge.targetId] == 0) {
                    queue.add(edge.targetId)
                }
            }
        }
        
        // Find the node with the longest path
        val endNode = longestPath.maxByOrNull { it.value }?.key ?: return emptyList()
        
        // Reconstruct path
        val path = mutableListOf<String>()
        var current: String? = endNode
        while (current != null) {
            path.add(0, current)
            current = predecessor[current]
        }
        
        return path
    }
    
    private suspend fun calculateModuleComplexity(moduleId: String): Float {
        val files = dao.getFilesInModule(moduleId)
        var totalComplexity = 0f
        
        for (file in files) {
            val symbols = dao.getSymbolsInFile(file.id!!)
            totalComplexity += symbols.sumOf { estimateSymbolComplexity(it).toDouble() }.toFloat()
        }
        
        return if (files.isNotEmpty()) totalComplexity / files.size else 0f
    }
    
    private suspend fun calculateMaintainability(moduleId: String): Float {
        // Simplified maintainability index
        // Real implementation would use Halstead metrics, cyclomatic complexity, LOC
        val lineCount = dao.getTotalLineCount(moduleId) ?: 0
        val fileCount = dao.getFileCount(moduleId)
        
        if (lineCount == 0L || fileCount == 0L) return 100f
        
        val avgFileSize = lineCount.toFloat() / fileCount
        
        // Smaller files = higher maintainability (simplified)
        return (100f * (1f - (avgFileSize / 500f).coerceAtMost(1f))).coerceAtLeast(0f)
    }
    
    private suspend fun calculateTestability(moduleId: String): Float {
        val symbols = mutableListOf<com.kmpforge.debugforge.persistence.IndexedSymbolEntity>()
        val files = dao.getFilesInModule(moduleId)
        
        for (file in files) {
            symbols.addAll(dao.getSymbolsInFile(file.id!!))
        }
        
        if (symbols.isEmpty()) return 100f
        
        // Higher testability for:
        // - More public functions (can be tested directly)
        // - Fewer dependencies (less mocking needed)
        // - More suspend functions (structured concurrency)
        
        val publicCount = symbols.count { it.visibility == "public" }
        val suspendCount = symbols.count { it.isSuspend }
        
        val publicRatio = publicCount.toFloat() / symbols.size
        val suspendBonus = (suspendCount.toFloat() / symbols.size) * 10f
        
        return (publicRatio * 80f + suspendBonus + 10f).coerceAtMost(100f)
    }
    
    private fun estimateSymbolComplexity(
        symbol: com.kmpforge.debugforge.persistence.IndexedSymbolEntity
    ): Float {
        var complexity = 1f
        
        // Function complexity factors
        if (symbol.kind == "function") {
            val lines = symbol.endLine - symbol.startLine
            complexity += lines * 0.1f
            
            if (symbol.isSuspend) complexity += 0.5f // Async adds complexity
            if (symbol.isInline) complexity += 0.3f // Inline has callsite expansion
        }
        
        // Class complexity factors
        if (symbol.kind == "class" || symbol.kind == "interface") {
            if (symbol.isSealed) complexity += 0.5f
            if (symbol.isDataClass) complexity -= 0.2f // Data classes are simpler
        }
        
        return complexity.coerceAtLeast(0f)
    }
    
    private fun calculateGraphMetrics(
        moduleNodes: List<ModuleNode>,
        moduleEdges: List<ModuleEdge>,
        symbolNodes: List<SymbolNode>,
        symbolEdges: List<SymbolEdge>
    ): GraphMetrics {
        val nodeCount = moduleNodes.size
        val edgeCount = moduleEdges.size
        
        // Calculate coupling (average outgoing edges per module)
        val avgCoupling = if (nodeCount > 0) {
            edgeCount.toFloat() / nodeCount
        } else 0f
        
        // Calculate cohesion (internal symbol references vs external)
        var internalRefs = 0
        var externalRefs = 0
        
        for (edge in symbolEdges) {
            val source = symbolNodes.find { it.id == edge.sourceId }
            val target = symbolNodes.find { it.id == edge.targetId }
            
            if (source != null && target != null) {
                if (source.moduleId == target.moduleId) {
                    internalRefs++
                } else {
                    externalRefs++
                }
            }
        }
        
        val cohesion = if (internalRefs + externalRefs > 0) {
            internalRefs.toFloat() / (internalRefs + externalRefs)
        } else 1f
        
        return GraphMetrics(
            moduleCount = nodeCount,
            edgeCount = edgeCount,
            symbolCount = symbolNodes.size,
            symbolEdgeCount = symbolEdges.size,
            avgCoupling = avgCoupling,
            avgCohesion = cohesion,
            expectCount = symbolNodes.count { it.isExpect },
            actualCount = symbolNodes.count { it.isActual }
        )
    }
}

// ============================================================================
// GRAPH DATA STRUCTURES
// ============================================================================

@Serializable
data class ProjectGraph(
    val moduleNodes: List<ModuleNode>,
    val moduleEdges: List<ModuleEdge>,
    val symbolNodes: List<SymbolNode>,
    val symbolEdges: List<SymbolEdge>,
    val metrics: GraphMetrics
)

@Serializable
data class ModuleNode(
    val id: String,
    val name: String,
    val path: String,
    val isKmp: Boolean,
    val hasCommonMain: Boolean,
    val platforms: List<String>,
    val fileCount: Int,
    val lineCount: Int,
    val metrics: ModuleMetrics
)

@Serializable
data class ModuleMetrics(
    val complexity: Float,
    val maintainability: Float,
    val testability: Float
)

@Serializable
data class ModuleEdge(
    val sourceId: String,
    val targetId: String,
    val dependencyType: DependencyType
)

@Serializable
enum class DependencyType {
    IMPLEMENTATION,
    API,
    COMPILE_ONLY,
    RUNTIME_ONLY,
    TEST
}

@Serializable
data class SymbolNode(
    val id: String,
    val name: String,
    val qualifiedName: String,
    val kind: SymbolKind,
    val moduleId: String,
    val sourceSet: String,
    val isExpect: Boolean,
    val isActual: Boolean,
    val visibility: Visibility,
    val metrics: SymbolMetrics
)

@Serializable
enum class SymbolKind {
    CLASS,
    INTERFACE,
    OBJECT,
    FUNCTION,
    PROPERTY,
    TYPEALIAS,
    ENUM,
    CONSTRUCTOR,
    ENUM_ENTRY,
    TYPE_ALIAS
}

@Serializable
enum class Visibility {
    PUBLIC,
    INTERNAL,
    PRIVATE,
    PROTECTED
}

@Serializable
data class SymbolMetrics(
    val referenceCount: Int,
    val complexity: Float,
    val cohesion: Float
)

@Serializable
data class SymbolEdge(
    val sourceId: String,
    val targetId: String,
    val referenceType: ReferenceType,
    val line: Int
)

@Serializable
enum class ReferenceType {
    CALL,
    TYPE_USE,
    IMPORT,
    INHERITANCE,
    ANNOTATION
}

@Serializable
data class GraphMetrics(
    val moduleCount: Int,
    val edgeCount: Int,
    val symbolCount: Int,
    val symbolEdgeCount: Int,
    val avgCoupling: Float,
    val avgCohesion: Float,
    val expectCount: Int,
    val actualCount: Int
)

@Serializable
data class CircularDependency(
    val modules: List<String>,
    val severity: CircularSeverity
)

@Serializable
enum class CircularSeverity {
    LOW,
    MEDIUM,
    HIGH
}

@Serializable
data class UnusedDependency(
    val moduleId: String,
    val declaredDependency: String,
    val declarationLocation: String
)
