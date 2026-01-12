package com.kmpforge.debugforge.app

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
actual open class SecureStorage {
    private val storageFile = File(System.getProperty("user.home"), ".debugforge/keys.enc")
    private val keyFile = File(System.getProperty("user.home"), ".debugforge/master.key")
    private val fallbackMap = mutableMapOf<String, String>() // Fallback storage when crypto fails
    private var useFallback = false

    init {
        try {
            storageFile.parentFile?.mkdirs()
        } catch (e: Exception) {
            // Ignore mkdirs failures
        }
    }

    private fun getOrCreateKey(): SecretKey? {
        try {
            if (keyFile.exists()) {
                val keyData = keyFile.readBytes()
                return javax.crypto.spec.SecretKeySpec(keyData, "AES")
            } else {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(256)
                val key = keyGen.generateKey()
                keyFile.writeBytes(key.encoded)
                return key
            }
        } catch (e: Exception) {
            // If crypto fails, use fallback
            useFallback = true
            return null
        }
    }

    private fun readAll(): MutableMap<String, String> {
        if (useFallback) return fallbackMap

        val secretKey = getOrCreateKey()
        if (secretKey == null || !storageFile.exists()) {
            useFallback = true
            return fallbackMap
        }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val data = Base64.decode(storageFile.readBytes())
            val iv = data.copyOfRange(0, 12)
            val encrypted = data.copyOfRange(12, data.size)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decrypted = cipher.doFinal(encrypted).decodeToString()
            // Parse as JSON map
            val map = mutableMapOf<String, String>()
            val regex = Regex("""\"(.*?)\":\s*\"(.*?)\"""")
            regex.findAll(decrypted).forEach { match ->
                val k = match.groupValues[1]
                val v = match.groupValues[2]
                map[k] = v
            }
            map
        } catch (e: Exception) {
            useFallback = true
            fallbackMap
        }
    }

    private fun writeAll(map: Map<String, String>) {
        if (useFallback) {
            fallbackMap.clear()
            fallbackMap.putAll(map)
            return
        }

        val secretKey = getOrCreateKey()
        if (secretKey == null) {
            useFallback = true
            fallbackMap.clear()
            fallbackMap.putAll(map)
            return
        }

        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.parameters.getParameterSpec(GCMParameterSpec::class.java).iv
            // Serialize as JSON
            val json = buildString {
                append("{")
                map.entries.forEachIndexed { i, (k, v) ->
                    append("\"")
                    append(k.replace("\"", "\\\""))
                    append("\":\"")
                    append(v.replace("\"", "\\\""))
                    append("\"")
                    if (i < map.size - 1) append(",")
                }
                append("}")
            }
            val encrypted = cipher.doFinal(json.toByteArray())
            val data = iv + encrypted
            storageFile.writeBytes(Base64.encode(data).toByteArray())
        } catch (e: Exception) {
            // If write fails, switch to fallback
            useFallback = true
            fallbackMap.clear()
            fallbackMap.putAll(map)
        }
    }

    actual open fun saveString(key: String, value: String) {
        try {
            val map = readAll()
            map[key] = value
            writeAll(map)
        } catch (e: Exception) {
            // Ignore save failures
        }
    }

    actual open fun getString(key: String): String? {
        return try {
            val map = readAll()
            map[key]
        } catch (e: Exception) {
            null
        }
    }

    actual open fun remove(key: String) {
        try {
            val map = readAll()
            map.remove(key)
            writeAll(map)
        } catch (e: Exception) {
            // Ignore remove failures
        }
    }

    actual open fun clearAll() {
        try {
            storageFile.delete()
            keyFile.delete()
        } catch (e: Exception) {
            // Ignore clear failures
        }
        fallbackMap.clear()
    }
}

actual fun createSecureStorage(): SecureStorage {
    return SecureStorage()
}