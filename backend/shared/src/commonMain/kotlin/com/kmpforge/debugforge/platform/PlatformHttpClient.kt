package com.kmpforge.debugforge.platform

/**
 * Platform-specific HTTP client abstraction.
 */
expect class PlatformHttpClient() {
    /**
     * Performs an HTTP GET request.
     */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse

    /**
     * Performs an HTTP POST request with a body.
     */
    suspend fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): HttpResponse

    /**
     * Performs an HTTP PUT request with a body.
     */
    suspend fun put(url: String, body: String, headers: Map<String, String> = emptyMap()): HttpResponse

    /**
     * Performs an HTTP DELETE request.
     */
    suspend fun delete(url: String, headers: Map<String, String> = emptyMap()): HttpResponse

    /**
     * Closes the HTTP client and releases resources.
     */
    fun close()
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
