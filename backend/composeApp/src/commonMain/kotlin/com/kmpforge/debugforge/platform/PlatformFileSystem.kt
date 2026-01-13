package com.kmpforge.debugforge.platform

expect class PlatformFileSystem() {
    fun exists(path: String): Boolean
    fun readFile(path: String): String
    fun writeFile(path: String, content: String)
    fun listFiles(path: String): List<String>
    fun isDirectory(path: String): Boolean
    fun getFileName(path: String): String
    suspend fun pickProjectFolder(): String?
}