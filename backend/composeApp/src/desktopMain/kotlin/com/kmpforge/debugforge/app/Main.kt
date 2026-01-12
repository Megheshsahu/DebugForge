package com.kmpforge.debugforge.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "DebugForge",
        state = rememberWindowState(width = 1280.dp, height = 800.dp)
    ) {
        App()
    }
}
