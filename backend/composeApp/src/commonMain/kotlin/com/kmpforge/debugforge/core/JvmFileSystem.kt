package com.kmpforge.debugforge.core

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

/**
 * JVM implementation of the FileSystem interface.
 */
class JvmFileSystem : FileSystem {
    override suspend fun exists(path: String): Boolean = File(path).exists()

    override suspend fun isDirectory(path: String): Boolean = File(path).isDirectory

    override suspend fun isFile(path: String): Boolean = File(path).isFile

    override suspend fun readFile(path: String): String = File(path).readText()

    override suspend fun readFileBytes(path: String): ByteArray = File(path).readBytes()

    override suspend fun writeFile(path: String, content: String) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    override suspend fun writeFileBytes(path: String, content: ByteArray) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(content)
    }

    override suspend fun deleteFile(path: String) {
        File(path).delete()
    }

    override suspend fun createDirectory(path: String) {
        File(path).mkdirs()
    }

    override suspend fun deleteDirectory(path: String) {
        File(path).deleteRecursively()
    }

    override suspend fun listDirectory(path: String): List<String> {
        return File(path).listFiles()?.map { it.name } ?: emptyList()
    }

    override suspend fun walkDirectory(path: String, filter: (String) -> Boolean): List<String> {
        return File(path).walk()
            .filter { filter(it.absolutePath) }
            .map { it.absolutePath }
            .toList()
    }

    override suspend fun computeFileHash(path: String): String {
        val bytes = File(path).readBytes()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    override suspend fun getFileSize(path: String): Long = File(path).length()

    override suspend fun getLastModified(path: String): Long = File(path).lastModified()

    override suspend fun copy(source: String, destination: String) {
        val sourceFile = File(source)
        val destFile = File(destination)
        destFile.parentFile?.mkdirs()
        Files.copy(sourceFile.toPath(), destFile.toPath())
    }

    override suspend fun move(source: String, destination: String) {
        val sourceFile = File(source)
        val destFile = File(destination)
        destFile.parentFile?.mkdirs()
        Files.move(sourceFile.toPath(), destFile.toPath())
    }

    override suspend fun getAbsolutePath(path: String): String = File(path).absolutePath

    override suspend fun getRelativePath(base: String, path: String): String {
        val basePath = Paths.get(base)
        val targetPath = Paths.get(path)
        return basePath.relativize(targetPath).toString()
    }

    override suspend fun getParent(path: String): String? = File(path).parent

    override suspend fun getFileName(path: String): String = File(path).name

    override suspend fun normalizePath(path: String): String = File(path).toPath().normalize().toString()

    override suspend fun resolvePath(base: String, relative: String): String {
        return Paths.get(base, relative).toString()
    }

    override suspend fun createTempFile(prefix: String, suffix: String): String {
        return File.createTempFile(prefix, suffix).absolutePath
    }

    override suspend fun createTempDirectory(prefix: String): String {
        return Files.createTempDirectory(prefix).toString()
    }
}