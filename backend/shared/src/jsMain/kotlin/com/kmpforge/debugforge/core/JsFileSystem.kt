package com.kmpforge.debugforge.core

/**
 * JS implementation of FileSystem using Node.js APIs.
 * TODO: Implement full functionality
 */
actual class JsFileSystem : FileSystem {
    actual override suspend fun exists(path: String): Boolean = TODO("Not yet implemented")
    actual override suspend fun isDirectory(path: String): Boolean = TODO("Not yet implemented")
    actual override suspend fun isFile(path: String): Boolean = TODO("Not yet implemented")
    actual override suspend fun readFile(path: String): String = TODO("Not yet implemented")
    actual override suspend fun readFileBytes(path: String): ByteArray = TODO("Not yet implemented")
    actual override suspend fun writeFile(path: String, content: String) = TODO("Not yet implemented")
    actual override suspend fun writeFileBytes(path: String, content: ByteArray) = TODO("Not yet implemented")
    actual override suspend fun deleteFile(path: String) = TODO("Not yet implemented")
    actual override suspend fun createDirectory(path: String) = TODO("Not yet implemented")
    actual override suspend fun deleteDirectory(path: String) = TODO("Not yet implemented")
    actual override suspend fun listDirectory(path: String): List<String> = TODO("Not yet implemented")
    actual override suspend fun walkDirectory(path: String, filter: (String) -> Boolean): List<String> = TODO("Not yet implemented")
    actual override suspend fun computeFileHash(path: String): String = TODO("Not yet implemented")
    actual override suspend fun getFileSize(path: String): Long = TODO("Not yet implemented")
    actual override suspend fun getLastModified(path: String): Long = TODO("Not yet implemented")
    actual override suspend fun copy(source: String, destination: String) = TODO("Not yet implemented")
    actual override suspend fun move(source: String, destination: String) = TODO("Not yet implemented")
    actual override suspend fun getAbsolutePath(path: String): String = TODO("Not yet implemented")
    actual override suspend fun getRelativePath(base: String, path: String): String = TODO("Not yet implemented")
    actual override suspend fun getParent(path: String): String? = TODO("Not yet implemented")
    actual override suspend fun getFileName(path: String): String = TODO("Not yet implemented")
    actual override suspend fun normalizePath(path: String): String = TODO("Not yet implemented")
    actual override suspend fun resolvePath(base: String, relative: String): String = TODO("Not yet implemented")
    actual override suspend fun createTempFile(prefix: String, suffix: String): String = TODO("Not yet implemented")
    actual override suspend fun createTempDirectory(prefix: String): String = TODO("Not yet implemented")
}