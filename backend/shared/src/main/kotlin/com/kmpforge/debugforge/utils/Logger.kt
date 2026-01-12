package com.kmpforge.debugforge.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Utility functions for logging with timestamps.
 * Local-only, never sends to external services.
 */
object DebugForgeLogger {
    private var enabled = true
    private var logLevel = LogLevel.INFO
    
    enum class LogLevel(val priority: Int) {
        DEBUG(0),
        INFO(1),
        WARN(2),
        ERROR(3)
    }
    
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
    
    fun setLevel(level: LogLevel) {
        this.logLevel = level
    }
    
    fun debug(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message)
    }
    
    fun info(tag: String, message: String) {
        log(LogLevel.INFO, tag, message)
    }
    
    fun warn(tag: String, message: String) {
        log(LogLevel.WARN, tag, message)
    }
    
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message)
        throwable?.let { 
            log(LogLevel.ERROR, tag, "Stacktrace: ${it.stackTraceToString()}")
        }
    }
    
    private fun log(level: LogLevel, tag: String, message: String) {
        if (!enabled || level.priority < logLevel.priority) return
        
        val timestamp = Clock.System.now()
        val levelStr = level.name.padEnd(5)
        println("[$timestamp] $levelStr [$tag] $message")
    }
}

/**
 * Extension function for timing operations.
 */
inline fun <T> timed(tag: String, operation: String, block: () -> T): T {
    val start = Clock.System.now()
    val result = block()
    val duration = Clock.System.now().toEpochMilliseconds() - start.toEpochMilliseconds()
    DebugForgeLogger.debug(tag, "$operation completed in ${duration}ms")
    return result
}

/**
 * Extension function for suspending timed operations.
 */
suspend inline fun <T> timedSuspend(tag: String, operation: String, block: suspend () -> T): T {
    val start = Clock.System.now()
    val result = block()
    val duration = Clock.System.now().toEpochMilliseconds() - start.toEpochMilliseconds()
    DebugForgeLogger.debug(tag, "$operation completed in ${duration}ms")
    return result
}
