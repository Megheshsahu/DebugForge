package com.kmpforge.debugforge.platform

actual class PlatformFileSystem {
    actual fun exists(path: String): Boolean {
        // TODO: Implement using File API or IndexedDB
        return false
    }
    
    actual fun readFile(path: String): String {
        // TODO: Implement file reading for Wasm
        throw UnsupportedOperationException("File reading not yet implemented for Web")
    }
    
    actual fun writeFile(path: String, content: String) {
        // TODO: Implement file writing (download) for Wasm
        throw UnsupportedOperationException("File writing not yet implemented for Web")
    }
    
    actual fun listFiles(path: String): List<String> {
        return emptyList()
    }
    
    actual fun isDirectory(path: String): Boolean = false
    
    actual fun getFileName(path: String): String {
        return path.substringAfterLast('/')
    }
    
    actual suspend fun pickProjectFolder(): String? {
        // TODO: Implement HTML5 File API integration
        return null
    }
}
