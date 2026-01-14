package com.kmpforge.debugforge.analysis

import com.kmpforge.debugforge.diagnostics.Diagnostic
import com.kmpforge.debugforge.diagnostics.DiagnosticCategory

/**
 * Interface for reading files - used by analyzers.
 * This abstraction allows analyzers to be platform-agnostic.
 */
interface FileSystemReader {
    /**
     * Reads the entire content of a file.
     */
    suspend fun readFile(path: String): String
    
    /**
     * Checks if a file exists.
     */
    suspend fun exists(path: String): Boolean
}

/**
 * Common interface for all analyzers.
 */
interface Analyzer {
    /**
     * Human-readable name for this analyzer.
     */
    val name: String
    
    /**
     * Category of diagnostics this analyzer produces.
     */
    val category: DiagnosticCategory
    
    /**
     * Runs analysis on a repository.
     */
    suspend fun analyze(repoPath: String): List<Diagnostic>
}
