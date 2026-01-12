package com.kmpforge.debugforge.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual class PlatformFileSystem {
    private var context: Context? = null
    private var projectUri: Uri? = null
    
    fun initialize(context: Context) {
        this.context = context
    }
    
    fun setProjectUri(uri: Uri) {
        this.projectUri = uri
    }
    
    actual fun exists(path: String): Boolean {
        val uri = projectUri ?: return false
        return findDocumentFile(uri, path) != null
    }
    
    actual fun readFile(path: String): String {
        val ctx = context ?: throw IllegalStateException("Context not initialized")
        val uri = projectUri ?: throw IllegalStateException("No project folder selected")
        
        val file = findDocumentFile(uri, path) 
            ?: throw IllegalArgumentException("File not found: $path")
        
        return ctx.contentResolver.openInputStream(file.uri)?.use {
            it.readBytes().decodeToString()
        } ?: ""
    }
    
    actual fun writeFile(path: String, content: String) {
        val ctx = context ?: throw IllegalStateException("Context not initialized")
        val uri = projectUri ?: throw IllegalStateException("No project folder selected")
        
        val file = findDocumentFile(uri, path) 
            ?: createDocumentFile(uri, path)
            ?: throw IllegalArgumentException("Cannot create file: $path")
        
        ctx.contentResolver.openOutputStream(file.uri)?.use {
            it.write(content.toByteArray())
        }
    }
    
    actual fun listFiles(path: String): List<String> {
        val uri = projectUri ?: return emptyList()
        val doc = if (path.isEmpty()) {
            DocumentFile.fromTreeUri(context!!, uri)
        } else {
            findDocumentFile(uri, path)
        } ?: return emptyList()
        
        return doc.listFiles().mapNotNull { it.name }
    }
    
    actual fun isDirectory(path: String): Boolean {
        val uri = projectUri ?: return false
        val doc = findDocumentFile(uri, path) ?: return false
        return doc.isDirectory
    }
    
    actual fun getFileName(path: String): String {
        return path.substringAfterLast('/')
    }
    
    actual suspend fun pickProjectFolder(): String? = suspendCancellableCoroutine { continuation ->
        val activity = context as? ComponentActivity 
            ?: run {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
        
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        val launcher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == ComponentActivity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    activity.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    projectUri = uri
                    continuation.resume(uri.toString())
                }
            } else {
                continuation.resume(null)
            }
        }
        launcher.launch(intent)
    }
    
    private fun findDocumentFile(baseUri: Uri, path: String): DocumentFile? {
        var current = DocumentFile.fromTreeUri(context!!, baseUri) ?: return null
        
        if (path.isEmpty()) return current
        
        val parts = path.split('/')
        for (part in parts) {
            if (part.isEmpty()) continue
            current = current.findFile(part) ?: return null
        }
        
        return current
    }
    
    private fun createDocumentFile(baseUri: Uri, path: String): DocumentFile? {
        val parts = path.split('/')
        var current = DocumentFile.fromTreeUri(context!!, baseUri) ?: return null
        
        for (i in 0 until parts.size - 1) {
            val part = parts[i]
            if (part.isEmpty()) continue
            current = current.findFile(part) ?: current.createDirectory(part) ?: return null
        }
        
        val fileName = parts.last()
        return current.createFile("text/plain", fileName)
    }
}
