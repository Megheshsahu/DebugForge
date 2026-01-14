package com.kmpforge.debugforge.core

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

actual object FileSystem {
    actual suspend fun exists(path: String): Boolean = File(path).exists()

    actual suspend fun isDirectory(path: String): Boolean = File(path).isDirectory

    actual suspend fun isFile(path: String): Boolean = File(path).isFile

    actual suspend fun readFile(path: String): String = File(path).readText()

    actual suspend fun readFileBytes(path: String): ByteArray = File(path).readBytes()

    actual suspend fun writeFile(path: String, content: String) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    actual suspend fun writeFileBytes(path: String, content: ByteArray) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(content)
    }

    actual suspend fun deleteFile(path: String) {
        File(path).delete()
    }

    actual suspend fun createDirectory(path: String) {
        File(path).mkdirs()
    }

    actual suspend fun deleteDirectory(path: String) {
        File(path).deleteRecursively()
    }

    actual suspend fun listDirectory(path: String): List<String> {
        return File(path).listFiles()?.map { it.name } ?: emptyList()
    }

    actual suspend fun walkDirectory(path: String, filter: (String) -> Boolean): List<String> {
        return File(path).walk()
            .filter { filter(it.absolutePath) }
            .map { it.absolutePath }
            .toList()
    }

    actual suspend fun computeFileHash(path: String): String {
        val bytes = File(path).readBytes()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    actual suspend fun getFileSize(path: String): Long = File(path).length()

    actual suspend fun getLastModified(path: String): Long = File(path).lastModified()

    actual suspend fun copy(source: String, destination: String) {
        val sourceFile = File(source)
        val destFile = File(destination)
        destFile.parentFile?.mkdirs()
        Files.copy(sourceFile.toPath(), destFile.toPath())
    }

    actual suspend fun move(source: String, destination: String) {
        val sourceFile = File(source)
        val destFile = File(destination)
        destFile.parentFile?.mkdirs()
        Files.move(sourceFile.toPath(), destFile.toPath())
    }

    actual suspend fun getAbsolutePath(path: String): String = File(path).absolutePath

    actual suspend fun getRelativePath(base: String, path: String): String {
        val basePath = Paths.get(base)
        val targetPath = Paths.get(path)
        return basePath.relativize(targetPath).toString()
    }

    actual suspend fun getParent(path: String): String? = File(path).parent

    actual suspend fun getFileName(path: String): String = File(path).name

    actual suspend fun normalizePath(path: String): String = File(path).toPath().normalize().toString()

    actual suspend fun resolvePath(base: String, relative: String): String {
        return Paths.get(base, relative).toString()
    }

    actual suspend fun createTempFile(prefix: String, suffix: String): String {
        return File.createTempFile(prefix, suffix).absolutePath
    }

    actual suspend fun createTempDirectory(prefix: String): String {
        return Files.createTempDirectory(prefix).toString()
    }
}
