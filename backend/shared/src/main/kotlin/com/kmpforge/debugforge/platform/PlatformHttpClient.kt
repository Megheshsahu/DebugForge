package com.kmpforge.debugforge.platform

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Platform-specific HTTP client abstraction.
 */
class PlatformHttpClient() {
    
    private val client = HttpClient(CIO) {
        expectSuccess = false
        engine {
            requestTimeout = 30000
            endpoint {
                connectTimeout = 10000
                keepAliveTime = 5000
            }
        }
    }
    
    /**
     * Performs an HTTP GET request.
     */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        return try {
            val response = client.get(url) {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
            }
            
            HttpResponse(
                statusCode = response.status.value,
                body = response.bodyAsText(),
                headers = extractHeaders(response)
            )
        } catch (e: Exception) {
            HttpResponse(
                statusCode = 0,
                body = "Error: ${e.message}",
                headers = emptyMap(),
                isError = true
            )
        }
    }
    
    /**
     * Performs an HTTP POST request.
     */
    suspend fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        return try {
            val response = client.post(url) {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
                setBody(body)
            }
            
            HttpResponse(
                statusCode = response.status.value,
                body = response.bodyAsText(),
                headers = extractHeaders(response)
            )
        } catch (e: Exception) {
            HttpResponse(
                statusCode = 0,
                body = "Error: ${e.message}",
                headers = emptyMap(),
                isError = true
            )
        }
    }
    
    /**
     * Performs an HTTP PUT request.
     */
    suspend fun put(url: String, body: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        return try {
            val response = client.put(url) {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
                setBody(body)
            }
            
            HttpResponse(
                statusCode = response.status.value,
                body = response.bodyAsText(),
                headers = extractHeaders(response)
            )
        } catch (e: Exception) {
            HttpResponse(
                statusCode = 0,
                body = "Error: ${e.message}",
                headers = emptyMap(),
                isError = true
            )
        }
    }
    
    /**
     * Performs an HTTP DELETE request.
     */
    suspend fun delete(url: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        return try {
            val response = client.delete(url) {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
            }
            
            HttpResponse(
                statusCode = response.status.value,
                body = response.bodyAsText(),
                headers = extractHeaders(response)
            )
        } catch (e: Exception) {
            HttpResponse(
                statusCode = 0,
                body = "Error: ${e.message}",
                headers = emptyMap(),
                isError = true
            )
        }
    }
    
    fun close() {
        client.close()
    }
    
    private fun extractHeaders(response: io.ktor.client.statement.HttpResponse): Map<String, String> {
        val result = mutableMapOf<String, String>()
        response.headers.names().forEach { name ->
            result[name] = response.headers[name] ?: ""
        }
        return result
    }
}

/**
 * HTTP response data class.
 */
data class HttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
    val isError: Boolean = false
) {
    val isSuccess: Boolean get() = statusCode in 200..299
}
