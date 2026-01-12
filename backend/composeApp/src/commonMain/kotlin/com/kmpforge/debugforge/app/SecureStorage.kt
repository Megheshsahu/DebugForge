package com.kmpforge.debugforge.app

/**
 * Secure storage for API keys and sensitive configuration
 */
expect class SecureStorage {
    fun saveString(key: String, value: String)
    fun getString(key: String): String?
    fun remove(key: String)
    fun clearAll()
}

/**
 * Factory function to create SecureStorage instance
 */
expect fun createSecureStorage(): SecureStorage