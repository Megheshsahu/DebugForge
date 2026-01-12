package com.kmpforge.debugforge.ai

import com.kmpforge.debugforge.analysis.FileSystemReader

/**
 * Generates git-style unified diffs from refactoring changes.
 * 
 * Produces standard unified diff format compatible with:
 * - git apply
 * - patch command
 * - IDE diff viewers
 */
class DiffGenerator(
    private val fileSystem: FileSystemReader
) {
    /**
     * Generates a unified diff from a list of text edits.
     */
    suspend fun generateUnifiedDiff(
        originalFilePath: String,
        edits: List<TextEditOperation>
    ): String {
        val originalContent = fileSystem.readFile(originalFilePath)
        val originalLines = originalContent.lines()
        
        // Apply edits to create modified content
        val modifiedLines = applyEdits(originalLines, edits)
        
        return generateDiff(
            originalFilePath,
            originalFilePath,
            originalLines,
            modifiedLines
        )
    }
    
    /**
     * Generates a diff comparing original and modified content.
     */
    fun generateDiff(
        originalPath: String,
        modifiedPath: String,
        originalLines: List<String>,
        modifiedLines: List<String>
    ): String {
        val hunks = computeHunks(originalLines, modifiedLines)
        
        if (hunks.isEmpty()) {
            return ""
        }
        
        val builder = StringBuilder()
        builder.appendLine("--- a/$originalPath")
        builder.appendLine("+++ b/$modifiedPath")
        
        hunks.forEach { hunk ->
            builder.appendLine("@@ -${hunk.originalStart},${hunk.originalCount} +${hunk.modifiedStart},${hunk.modifiedCount} @@")
            
            hunk.lines.forEach { line ->
                val prefix = when (line.type) {
                    DiffLineType.CONTEXT -> " "
                    DiffLineType.ADDITION -> "+"
                    DiffLineType.DELETION -> "-"
                }
                builder.appendLine("$prefix${line.content}")
            }
        }
        
        return builder.toString()
    }
    
    /**
     * Generates a multi-file diff for complex refactorings.
     */
    suspend fun generateMultiFileDiff(
        changes: List<FileChangeSpec>
    ): String {
        val builder = StringBuilder()
        
        changes.forEach { change ->
            when (change) {
                is FileChangeSpec.Modified -> {
                    val original = fileSystem.readFile(change.filePath)
                    val modified = applyEdits(original.lines(), change.edits)
                    builder.append(generateDiff(change.filePath, change.filePath, original.lines(), modified))
                    builder.appendLine()
                }
                is FileChangeSpec.Added -> {
                    builder.appendLine("--- /dev/null")
                    builder.appendLine("+++ b/${change.filePath}")
                    val lines = change.content.lines()
                    builder.appendLine("@@ -0,0 +1,${lines.size} @@")
                    lines.forEach { line ->
                        builder.appendLine("+$line")
                    }
                    builder.appendLine()
                }
                is FileChangeSpec.Deleted -> {
                    val original = fileSystem.readFile(change.filePath)
                    val lines = original.lines()
                    builder.appendLine("--- a/${change.filePath}")
                    builder.appendLine("+++ /dev/null")
                    builder.appendLine("@@ -1,${lines.size} +0,0 @@")
                    lines.forEach { line ->
                        builder.appendLine("-$line")
                    }
                    builder.appendLine()
                }
                is FileChangeSpec.Renamed -> {
                    builder.appendLine("rename from ${change.oldPath}")
                    builder.appendLine("rename to ${change.newPath}")
                    if (change.edits.isNotEmpty()) {
                        val original = fileSystem.readFile(change.oldPath)
                        val modified = applyEdits(original.lines(), change.edits)
                        builder.append(generateDiff(change.oldPath, change.newPath, original.lines(), modified))
                    }
                    builder.appendLine()
                }
            }
        }
        
        return builder.toString()
    }
    
    /**
     * Parses a unified diff back into structured changes.
     */
    fun parseDiff(diffContent: String): List<ParsedDiffFile> {
        val files = mutableListOf<ParsedDiffFile>()
        var currentFile: ParsedDiffFile? = null
        var currentHunk: MutableList<DiffLine>? = null
        var hunkOriginalStart = 0
        var hunkOriginalCount = 0
        var hunkModifiedStart = 0
        var hunkModifiedCount = 0
        
        diffContent.lines().forEach { line ->
            when {
                line.startsWith("--- ") -> {
                    currentFile?.let { 
                        if (currentHunk != null) {
                            it.hunks.add(DiffHunk(
                                hunkOriginalStart, hunkOriginalCount,
                                hunkModifiedStart, hunkModifiedCount,
                                currentHunk!!.toList()
                            ))
                        }
                        files.add(it) 
                    }
                    currentFile = ParsedDiffFile(
                        originalPath = line.removePrefix("--- a/").removePrefix("--- "),
                        modifiedPath = "",
                        hunks = mutableListOf()
                    )
                    currentHunk = null
                }
                line.startsWith("+++ ") -> {
                    currentFile?.let {
                        files.removeLastOrNull()
                        currentFile = it.copy(
                            modifiedPath = line.removePrefix("+++ b/").removePrefix("+++ ")
                        )
                    }
                }
                line.startsWith("@@ ") -> {
                    currentHunk?.let { hunk ->
                        currentFile?.hunks?.add(DiffHunk(
                            hunkOriginalStart, hunkOriginalCount,
                            hunkModifiedStart, hunkModifiedCount,
                            hunk.toList()
                        ))
                    }
                    
                    val match = HUNK_HEADER.find(line)
                    if (match != null) {
                        hunkOriginalStart = match.groupValues[1].toInt()
                        hunkOriginalCount = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
                        hunkModifiedStart = match.groupValues[3].toInt()
                        hunkModifiedCount = match.groupValues.getOrNull(4)?.toIntOrNull() ?: 1
                    }
                    currentHunk = mutableListOf()
                }
                line.startsWith("+") && currentHunk != null -> {
                    currentHunk!!.add(DiffLine(
                        type = DiffLineType.ADDITION,
                        content = line.removePrefix("+"),
                        originalLineNumber = null,
                        modifiedLineNumber = null
                    ))
                }
                line.startsWith("-") && currentHunk != null -> {
                    currentHunk!!.add(DiffLine(
                        type = DiffLineType.DELETION,
                        content = line.removePrefix("-"),
                        originalLineNumber = null,
                        modifiedLineNumber = null
                    ))
                }
                line.startsWith(" ") && currentHunk != null -> {
                    currentHunk!!.add(DiffLine(
                        type = DiffLineType.CONTEXT,
                        content = line.removePrefix(" "),
                        originalLineNumber = null,
                        modifiedLineNumber = null
                    ))
                }
            }
        }
        
        // Add last file
        currentFile?.let { file ->
            currentHunk?.let { hunk ->
                file.hunks.add(DiffHunk(
                    hunkOriginalStart, hunkOriginalCount,
                    hunkModifiedStart, hunkModifiedCount,
                    hunk.toList()
                ))
            }
            files.add(file)
        }
        
        return files
    }
    
    /**
     * Applies a parsed diff to a file.
     */
    suspend fun applyDiff(filePath: String, diff: ParsedDiffFile): String {
        val original = fileSystem.readFile(filePath).lines().toMutableList()
        var offset = 0
        
        diff.hunks.forEach { hunk ->
            val insertPoint = hunk.originalStart - 1 + offset
            var deletions = 0
            val additions = mutableListOf<String>()
            
            hunk.lines.forEach { line ->
                when (line.type) {
                    DiffLineType.DELETION -> deletions++
                    DiffLineType.ADDITION -> additions.add(line.content)
                    DiffLineType.CONTEXT -> {}
                }
            }
            
            // Remove deleted lines
            repeat(deletions) {
                if (insertPoint < original.size) {
                    original.removeAt(insertPoint)
                }
            }
            
            // Add new lines
            additions.forEachIndexed { index, addedLine ->
                original.add(insertPoint + index, addedLine)
            }
            
            offset += additions.size - deletions
        }
        
        return original.joinToString("\n")
    }
    
    private fun applyEdits(lines: List<String>, edits: List<TextEditOperation>): List<String> {
        val result = lines.toMutableList()
        
        // Sort edits by line number in reverse order to maintain line numbers
        val sortedEdits = edits.sortedByDescending { it.startLine }
        
        sortedEdits.forEach { edit ->
            val startIndex = (edit.startLine - 1).coerceAtLeast(0)
            val endIndex = (edit.endLine).coerceAtMost(result.size)
            
            // Remove lines in range
            for (i in startIndex until endIndex) {
                if (startIndex < result.size) {
                    result.removeAt(startIndex)
                }
            }
            
            // Insert new content
            val newLines = edit.newContent.lines()
            newLines.forEachIndexed { index, line ->
                result.add(startIndex + index, line)
            }
        }
        
        return result
    }
    
    private fun computeHunks(original: List<String>, modified: List<String>): List<DiffHunk> {
        // Simple LCS-based diff algorithm
        val lcs = computeLCS(original, modified)
        val hunks = mutableListOf<DiffHunk>()
        
        var originalIndex = 0
        var modifiedIndex = 0
        var lcsIndex = 0
        
        while (originalIndex < original.size || modifiedIndex < modified.size) {
            // Find start of changed section
            while (lcsIndex < lcs.size && 
                   originalIndex < original.size && 
                   modifiedIndex < modified.size &&
                   original[originalIndex] == lcs[lcsIndex] &&
                   modified[modifiedIndex] == lcs[lcsIndex]) {
                originalIndex++
                modifiedIndex++
                lcsIndex++
            }
            
            if (originalIndex >= original.size && modifiedIndex >= modified.size) {
                break
            }
            
            // Collect changed lines
            val hunkLines = mutableListOf<DiffLine>()
            val hunkOriginalStart = originalIndex + 1
            val hunkModifiedStart = modifiedIndex + 1
            
            // Add context before (up to 3 lines)
            val contextStart = maxOf(0, originalIndex - 3)
            for (i in contextStart until originalIndex) {
                hunkLines.add(DiffLine(DiffLineType.CONTEXT, original[i], i + 1, i + 1))
            }
            
            // Find deletions
            while (originalIndex < original.size && 
                   (lcsIndex >= lcs.size || original[originalIndex] != lcs[lcsIndex])) {
                hunkLines.add(DiffLine(DiffLineType.DELETION, original[originalIndex], originalIndex + 1, null))
                originalIndex++
            }
            
            // Find additions
            while (modifiedIndex < modified.size &&
                   (lcsIndex >= lcs.size || modified[modifiedIndex] != lcs[lcsIndex])) {
                hunkLines.add(DiffLine(DiffLineType.ADDITION, modified[modifiedIndex], null, modifiedIndex + 1))
                modifiedIndex++
            }
            
            // Add context after (up to 3 lines)
            val contextEnd = minOf(original.size, originalIndex + 3)
            for (i in originalIndex until contextEnd) {
                if (lcsIndex < lcs.size && original[i] == lcs[lcsIndex]) {
                    hunkLines.add(DiffLine(DiffLineType.CONTEXT, original[i], i + 1, i + 1))
                    originalIndex++
                    modifiedIndex++
                    lcsIndex++
                }
            }
            
            if (hunkLines.any { it.type != DiffLineType.CONTEXT }) {
                hunks.add(DiffHunk(
                    originalStart = hunkOriginalStart,
                    originalCount = hunkLines.count { it.type != DiffLineType.ADDITION },
                    modifiedStart = hunkModifiedStart,
                    modifiedCount = hunkLines.count { it.type != DiffLineType.DELETION },
                    lines = hunkLines
                ))
            }
        }
        
        return hunks
    }
    
    private fun computeLCS(a: List<String>, b: List<String>): List<String> {
        val m = a.size
        val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        
        // Backtrack to find LCS
        val lcs = mutableListOf<String>()
        var i = m
        var j = n
        
        while (i > 0 && j > 0) {
            when {
                a[i - 1] == b[j - 1] -> {
                    lcs.add(0, a[i - 1])
                    i--
                    j--
                }
                dp[i - 1][j] > dp[i][j - 1] -> i--
                else -> j--
            }
        }
        
        return lcs
    }
    
    companion object {
        private val HUNK_HEADER = Regex("""@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")
    }
}

/**
 * A text edit operation.
 */
data class TextEditOperation(
    val startLine: Int,
    val endLine: Int,
    val newContent: String
)

/**
 * Specification for a file change in a multi-file diff.
 */
sealed class FileChangeSpec {
    data class Modified(
        val filePath: String,
        val edits: List<TextEditOperation>
    ) : FileChangeSpec()
    
    data class Added(
        val filePath: String,
        val content: String
    ) : FileChangeSpec()
    
    data class Deleted(
        val filePath: String
    ) : FileChangeSpec()
    
    data class Renamed(
        val oldPath: String,
        val newPath: String,
        val edits: List<TextEditOperation> = emptyList()
    ) : FileChangeSpec()
}

/**
 * Parsed diff file structure.
 */
data class ParsedDiffFile(
    val originalPath: String,
    val modifiedPath: String,
    val hunks: MutableList<DiffHunk>
)
