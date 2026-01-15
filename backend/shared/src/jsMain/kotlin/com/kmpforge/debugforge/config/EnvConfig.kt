package com.kmpforge.debugforge.config

import kotlin.js.Json
import kotlin.js.json

@JsName("process")
external val process: dynamic

actual fun getEnvVariable(name: String): String? {
    return try {
        process.env[name] as? String
    } catch (e: Exception) {
        null
    }
}