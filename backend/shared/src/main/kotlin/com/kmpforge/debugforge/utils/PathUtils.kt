package com.kmpforge.debugforge.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Custom serializers for KMP compatibility.
 */
object PathSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: String) {
        // Normalize path separators for cross-platform
        encoder.encodeString(value.replace('\\', '/'))
    }
    
    override fun deserialize(decoder: Decoder): String {
        return decoder.decodeString().replace('\\', '/')
    }
}

/**
 * Path utilities for cross-platform path handling.
 */
object PathUtils {
    /**
     * Normalizes a path to use forward slashes.
     */
    fun normalize(path: String): String = path.replace('\\', '/')
    
    /**
     * Joins path segments.
     */
    fun join(vararg segments: String): String {
        return segments.joinToString("/") { it.trim('/') }
    }
    
    /**
     * Gets the file name from a path.
     */
    fun fileName(path: String): String {
        val normalized = normalize(path)
        return normalized.substringAfterLast('/')
    }
    
    /**
     * Gets the directory from a path.
     */
    fun directory(path: String): String {
        val normalized = normalize(path)
        return normalized.substringBeforeLast('/', "")
    }
    
    /**
     * Gets the file extension.
     */
    fun extension(path: String): String {
        val name = fileName(path)
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) name.substring(dotIndex + 1) else ""
    }
    
    /**
     * Checks if a path is absolute.
     */
    fun isAbsolute(path: String): Boolean {
        val normalized = normalize(path)
        return normalized.startsWith('/') || 
               (normalized.length >= 2 && normalized[1] == ':') // Windows drive
    }
    
    /**
     * Gets relative path from base to target.
     */
    fun relativize(base: String, target: String): String {
        val baseParts = normalize(base).split('/').filter { it.isNotEmpty() }
        val targetParts = normalize(target).split('/').filter { it.isNotEmpty() }
        
        var commonLength = 0
        for (i in 0 until minOf(baseParts.size, targetParts.size)) {
            if (baseParts[i] == targetParts[i]) {
                commonLength++
            } else {
                break
            }
        }
        
        val upCount = baseParts.size - commonLength
        val ups = List(upCount) { ".." }
        val remaining = targetParts.drop(commonLength)
        
        return (ups + remaining).joinToString("/")
    }
}
