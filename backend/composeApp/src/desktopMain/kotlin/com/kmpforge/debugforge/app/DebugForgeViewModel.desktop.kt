package com.kmpforge.debugforge.app

actual fun startServerPlatform(viewModel: DebugForgeViewModel, groqApiKey: String, githubToken: String): Boolean {
    println("DEBUG: startServerPlatform called with groqApiKey=${groqApiKey.take(10)}..., githubToken=${githubToken.take(10)}...")
    val success = DesktopServerController.startServer(viewModel, groqApiKey, githubToken)
    println("DEBUG: DesktopServerController.startServer returned: $success")
    return success
}

actual fun stopServerPlatform(viewModel: DebugForgeViewModel) {
    println("DEBUG: stopServerPlatform called")
    DesktopServerController.stopServer()
}

actual fun isServerRunningPlatform(viewModel: DebugForgeViewModel): Boolean {
    val running = DesktopServerController.isServerRunning()
    println("DEBUG: isServerRunningPlatform called, returning: $running")
    return running
}
