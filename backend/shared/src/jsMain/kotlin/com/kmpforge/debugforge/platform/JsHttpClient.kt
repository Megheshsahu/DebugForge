package com.kmpforge.debugforge.platform

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * JS-specific HTTP client using Ktor JS engine.
 */
actual class PlatformHttpClient actual constructor() {
    
    private val client = HttpClient(Js) {
        expectSuccess = false
    }
    
    /**
     * Performs an HTTP GET request.
     */
    actual suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
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
    actual suspend fun post(url: String, body: String, headers: Map<String, String>): HttpResponse {
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
    actual suspend fun put(url: String, body: String, headers: Map<String, String>): HttpResponse {
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
    actual suspend fun delete(url: String, headers: Map<String, String>): HttpResponse {
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
    
    actual fun close() {
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
