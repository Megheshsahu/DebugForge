package com.kmpforge.debugforge.platform

import java.net.HttpURLConnection
import java.net.URL

/**
 * JVM implementation of PlatformHttpClient using java.net.HttpURLConnection.
 */
actual class PlatformHttpClient actual constructor() {

    actual suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        return executeRequest("GET", url, null, headers)
    }

    actual suspend fun post(url: String, body: String, headers: Map<String, String>): HttpResponse {
        return executeRequest("POST", url, body, headers)
    }

    actual suspend fun put(url: String, body: String, headers: Map<String, String>): HttpResponse {
        return executeRequest("PUT", url, body, headers)
    }

    actual suspend fun delete(url: String, headers: Map<String, String>): HttpResponse {
        return executeRequest("DELETE", url, null, headers)
    }

    actual fun close() {
        // HttpURLConnection doesn't need explicit closing
    }

    private suspend fun executeRequest(method: String, url: String, body: String?, headers: Map<String, String>): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        // Set headers
        headers.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }

        // Set content type for requests with body
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
        }

        return try {
            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            HttpResponse(responseCode, responseBody)
        } catch (e: Exception) {
            HttpResponse(0, "Network error: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }
}