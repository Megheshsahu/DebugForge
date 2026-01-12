package com.kmpforge.debugforge.platform

import java.io.File
import javax.swing.JFileChooser

/**
 * Platform-specific file system abstraction.
 */
class PlatformFileSystem {
    fun exists(path: String): Boolean = File(path).exists()
    
    fun readFile(path: String): String = File(path).readText()
    
    fun writeFile(path: String, content: String) {
        File(path).writeText(content)
    }
    
    fun listFiles(path: String): List<String> {
        return File(path).listFiles()?.map { it.absolutePath } ?: emptyList()
    }
    
    fun isDirectory(path: String): Boolean = File(path).isDirectory
    
    fun getFileName(path: String): String = File(path).name
    
    suspend fun pickProjectFolder(): String? {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = "Select Project Folder"
        
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath
        } else {
            null
        }
    }
}