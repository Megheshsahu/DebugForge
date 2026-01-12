package com.kmpforge.debugforge.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.coroutines.resume

actual class PlatformFileSystem {
    actual fun exists(path: String): Boolean = File(path).exists()

    actual fun readFile(path: String): String = File(path).readText()

    actual fun writeFile(path: String, content: String) {
        File(path).writeText(content)
    }

    actual fun listFiles(path: String): List<String> {
        val file = File(path)
        return if (file.isDirectory) {
            file.listFiles()?.map { it.name } ?: emptyList()
        } else {
            emptyList()
        }
    }

    actual fun isDirectory(path: String): Boolean = File(path).isDirectory

    actual fun getFileName(path: String): String = File(path).name

    actual suspend fun pickProjectFolder(): String? = suspendCancellableCoroutine { continuation ->
        // For desktop, we could show a file chooser dialog
        // For now, return null as this is a placeholder
        continuation.resume(null)
    }
}