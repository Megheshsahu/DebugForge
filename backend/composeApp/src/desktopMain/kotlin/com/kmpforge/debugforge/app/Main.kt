package com.kmpforge.debugforge.app

import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.unit.dp
import com.kmpforge.debugforge.utils.DebugForgeLogger

fun main() {
    try {
        DebugForgeLogger.info("Main", "DebugForge desktop app starting...")
        application {
            Window(
                onCloseRequest = {
                    DebugForgeLogger.info("Main", "Window closing...")
                    // Optionally stop server if needed
                    exitApplication()
                },
                title = "DebugForge",
                state = rememberWindowState(width = 1280.dp, height = 800.dp, position = WindowPosition(100.dp, 100.dp))
            ) {
                DebugForgeLogger.debug("Main", "Window created, calling App()")
                App()
            }
        }
    } catch (e: Exception) {
        DebugForgeLogger.error("Main", "Exception in main: ${e.message}", e)
    }
}
