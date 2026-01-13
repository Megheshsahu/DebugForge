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
        try {
            // Use Swing JFileChooser for desktop file selection
            javax.swing.SwingUtilities.invokeLater {
                val fileChooser = javax.swing.JFileChooser().apply {
                    fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Select Kotlin Multiplatform Project Directory"
                    approveButtonText = "Select Project"
                    approveButtonToolTipText = "Select the root directory of your KMP project"

                    // Set current directory to a reasonable default
                    currentDirectory = java.io.File(System.getProperty("user.home"))
                }

                val result = fileChooser.showOpenDialog(null)
                if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                    val selectedFile = fileChooser.selectedFile
                    if (selectedFile != null && selectedFile.isDirectory) {
                        continuation.resume(selectedFile.absolutePath)
                    } else {
                        continuation.resume(null)
                    }
                } else {
                    continuation.resume(null)
                }
            }
        } catch (e: Exception) {
            println("Error opening file chooser: ${e.message}")
            continuation.resume(null)
        }
    }
}