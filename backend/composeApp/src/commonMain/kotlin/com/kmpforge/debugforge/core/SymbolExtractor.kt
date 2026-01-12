package com.kmpforge.debugforge.core

import com.kmpforge.debugforge.persistence.IndexedSymbolEntity
import com.kmpforge.debugforge.persistence.SymbolReferenceEntity

/**
 * Interface for extracting symbols from source files.
 */
interface SymbolExtractor {
    /**
     * Extracts symbols and references from a source file.
     */
    suspend fun extract(file: SourceFile): ExtractionResult
}

/**
 * Result of symbol extraction.
 */
data class ExtractionResult(
    val symbols: List<IndexedSymbolEntity>,
    val references: List<SymbolReferenceEntity>
)

/**
 * Factory for creating the best available symbol extractor.
 * Implementations can provide platform-specific extractors.
 */
interface SymbolExtractorFactory {
    fun create(): SymbolExtractor
}

/**
 * Default factory that creates a regex-based extractor.
 * JVM implementations override this with KSP-based extractor.
 */
object DefaultSymbolExtractorFactory : SymbolExtractorFactory {
    override fun create(): SymbolExtractor = RegexSymbolExtractor()
}

/**
 * Simple regex-based symbol extractor.
 * This is a basic implementation for initial development.
 * For production, this would be enhanced with KSP or Kotlin compiler API.
 */
class RegexSymbolExtractor : SymbolExtractor {
    
    private val classPattern = Regex(
        """(?:^|\n)\s*(?:(expect|actual)\s+)?(?:(data|sealed|enum|annotation|value|inline|open|abstract|internal|public|private|protected)\s+)*class\s+(\w+)""",
        RegexOption.MULTILINE
    )
    
    private val objectPattern = Regex(
        """(?:^|\n)\s*(?:(expect|actual)\s+)?(?:(internal|public|private|protected)\s+)*object\s+(\w+)""",
        RegexOption.MULTILINE
    )
    
    private val interfacePattern = Regex(
        """(?:^|\n)\s*(?:(expect|actual)\s+)?(?:(internal|public|private|protected)\s+)*interface\s+(\w+)""",
        RegexOption.MULTILINE
    )
    
    private val functionPattern = Regex(
        """(?:^|\n)\s*(?:(expect|actual)\s+)?(?:(suspend|inline|infix|operator|tailrec|internal|public|private|protected|open|override|abstract)\s+)*fun\s+(?:<[^>]+>\s*)?(\w+)\s*\(""",
        RegexOption.MULTILINE
    )
    
    private val propertyPattern = Regex(
        """(?:^|\n)\s*(?:(expect|actual)\s+)?(?:(override|abstract|open|internal|public|private|protected|lateinit|const)\s+)*(?:val|var)\s+(\w+)\s*[:\=]""",
        RegexOption.MULTILINE
    )
    
    override suspend fun extract(file: SourceFile): ExtractionResult {
        val symbols = mutableListOf<IndexedSymbolEntity>()
        val references = mutableListOf<SymbolReferenceEntity>()
        
        val content = file.content
        var fileId: Long = 0  // Will be set later when persisted
        
        // Extract classes
        classPattern.findAll(content).forEach { match ->
            val (expectActual, _, name) = match.destructured
            val lineNumber = countLines(content, match.range.first)
            symbols.add(createSymbol(
                fileId = fileId,
                name = name,
                qualifiedName = "${file.packageName?.let { "$it." } ?: ""}$name",
                kind = "CLASS",
                isExpect = expectActual == "expect",
                isActual = expectActual == "actual",
                startLine = lineNumber
            ))
        }
        
        // Extract objects
        objectPattern.findAll(content).forEach { match ->
            val (expectActual, _, name) = match.destructured
            val lineNumber = countLines(content, match.range.first)
            symbols.add(createSymbol(
                fileId = fileId,
                name = name,
                qualifiedName = "${file.packageName?.let { "$it." } ?: ""}$name",
                kind = "OBJECT",
                isExpect = expectActual == "expect",
                isActual = expectActual == "actual",
                startLine = lineNumber
            ))
        }
        
        // Extract interfaces
        interfacePattern.findAll(content).forEach { match ->
            val (expectActual, _, name) = match.destructured
            val lineNumber = countLines(content, match.range.first)
            symbols.add(createSymbol(
                fileId = fileId,
                name = name,
                qualifiedName = "${file.packageName?.let { "$it." } ?: ""}$name",
                kind = "INTERFACE",
                isExpect = expectActual == "expect",
                isActual = expectActual == "actual",
                startLine = lineNumber
            ))
        }
        
        // Extract functions
        functionPattern.findAll(content).forEach { match ->
            val groups = match.groups
            val expectActual = groups[1]?.value ?: ""
            val modifiers = groups[2]?.value ?: ""
            val name = groups[3]?.value
            if (name != null) {
                val lineNumber = countLines(content, match.range.first)
                symbols.add(createSymbol(
                    fileId = fileId,
                    name = name,
                    qualifiedName = "${file.packageName?.let { "$it." } ?: ""}$name",
                    kind = "FUNCTION",
                    isExpect = expectActual == "expect",
                    isActual = expectActual == "actual",
                    isSuspend = modifiers.contains("suspend"),
                    startLine = lineNumber
                ))
            }
        }
        
        // Extract properties
        propertyPattern.findAll(content).forEach { match ->
            val groups = match.groups
            val expectActual = groups[1]?.value ?: ""
            val name = groups[3]?.value
            if (name != null) {
                val lineNumber = countLines(content, match.range.first)
                symbols.add(createSymbol(
                    fileId = fileId,
                    name = name,
                    qualifiedName = "${file.packageName?.let { "$it." } ?: ""}$name",
                    kind = "PROPERTY",
                    isExpect = expectActual == "expect",
                    isActual = expectActual == "actual",
                    startLine = lineNumber
                ))
            }
        }
        
        return ExtractionResult(symbols, references)
    }
    
    private fun countLines(content: String, charIndex: Int): Int {
        return content.substring(0, charIndex).count { it == '\n' } + 1
    }
    
    private fun createSymbol(
        fileId: Long,
        name: String,
        qualifiedName: String,
        kind: String,
        isExpect: Boolean,
        isActual: Boolean,
        isSuspend: Boolean = false,
        startLine: Int
    ): IndexedSymbolEntity {
        return IndexedSymbolEntity(
            id = null,
            fileId = fileId,
            name = name,
            qualifiedName = qualifiedName,
            kind = kind,
            visibility = "PUBLIC",
            isExpect = isExpect,
            isActual = isActual,
            isSuspend = isSuspend,
            isInline = false,
            isDataClass = false,
            isSealed = false,
            isCompanion = false,
            isExtension = false,
            startLine = startLine,
            endLine = startLine,
            startColumn = 0,
            endColumn = 0,
            signature = null,
            parentSymbolId = null,
            annotations = null
        )
    }
}
