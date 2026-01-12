package com.kmpforge.debugforge.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

actual class SecureStorage(private val context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "debugforge_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    actual fun saveString(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }

    actual fun getString(key: String): String? {
        return sharedPrefs.getString(key, null)
    }

    actual fun remove(key: String) {
        sharedPrefs.edit().remove(key).apply()
    }

    actual fun clearAll() {
        sharedPrefs.edit().clear().apply()
    }
}

actual fun createSecureStorage(): SecureStorage {
    // This will be called from Android-specific code with context
    // For now, return a placeholder - will be fixed when integrating
    TODO("SecureStorage needs Android Context - implement in Android app initialization")
}