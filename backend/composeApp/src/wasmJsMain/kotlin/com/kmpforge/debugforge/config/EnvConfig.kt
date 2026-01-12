package com.kmpforge.debugforge.config

import kotlinx.browser.window

actual fun getEnvVariable(name: String): String? {
    // In browser/WASM, we can't access environment variables
    // Use a JavaScript object for configuration instead
    return try {
        js("window['ENV_' + name]") as? String
    } catch (e: Exception) {
        null
    }
}
