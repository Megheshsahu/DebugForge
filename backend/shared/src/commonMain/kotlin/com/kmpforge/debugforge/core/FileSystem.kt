package com.kmpforge.debugforge.core

/**
 * Platform-agnostic file system abstraction.
 * Implemented differently on JVM, Native, and JS platforms.
 */
expect object FileSystem {
    /**
     * Checks if a file or directory exists.
     */
    suspend fun exists(path: String): Boolean

    /**
     * Checks if path is a directory.
     */
    suspend fun isDirectory(path: String): Boolean

    /**
     * Checks if path is a file.
     */
    suspend fun isFile(path: String): Boolean

    /**
     * Reads the entire content of a file as a string.
     */
    suspend fun readFile(path: String): String

    /**
     * Reads the entire content of a file as bytes.
     */
    suspend fun readFileBytes(path: String): ByteArray

    /**
     * Writes content to a file, creating it if necessary.
     */
    suspend fun writeFile(path: String, content: String)

    /**
     * Writes bytes to a file, creating it if necessary.
     */
    suspend fun writeFileBytes(path: String, content: ByteArray)

    /**
     * Deletes a file.
     */
    suspend fun deleteFile(path: String)

    /**
     * Creates a directory and all parent directories.
     */
    suspend fun createDirectory(path: String)

    /**
     * Deletes a directory recursively.
     */
    suspend fun deleteDirectory(path: String)

    /**
     * Lists immediate children of a directory.
     * Returns file/directory names, not full paths.
     */
    suspend fun listDirectory(path: String): List<String>

    /**
     * Recursively walks a directory and returns all file paths.
     * @param filter Optional filter for which files to include
     */
    suspend fun walkDirectory(path: String, filter: (String) -> Boolean = { true }): List<String>

    /**
     * Computes a hash of file content for change detection.
     */
    suspend fun computeFileHash(path: String): String

    /**
     * Gets the file size in bytes.
     */
    suspend fun getFileSize(path: String): Long

    /**
     * Gets the last modification timestamp.
     */
    suspend fun getLastModified(path: String): Long

    /**
     * Copies a file from source to destination.
     */
    suspend fun copy(source: String, destination: String)

    /**
     * Moves a file from source to destination.
     */
    suspend fun move(source: String, destination: String)

    /**
     * Gets absolute path.
     */
    suspend fun getAbsolutePath(path: String): String

    /**
     * Gets relative path from base to target.
     */
    suspend fun getRelativePath(base: String, path: String): String

    /**
     * Gets the parent directory path.
     */
    suspend fun getParent(path: String): String?

    /**
     * Gets the file name from a path.
     */
    suspend fun getFileName(path: String): String

    /**
     * Normalizes path separators.
     */
    suspend fun normalizePath(path: String): String

    /**
     * Resolves a path relative to a base path.
     */
    suspend fun resolvePath(base: String, relative: String): String

    /**
     * Creates a temporary file.
     */
    suspend fun createTempFile(prefix: String, suffix: String): String

    /**
     * Creates a temporary directory.
     */
    suspend fun createTempDirectory(prefix: String): String
}
