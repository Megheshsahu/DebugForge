package com.kmpforge.debugforge.app

actual fun startServerPlatform(viewModel: DebugForgeViewModel, groqApiKey: String, githubToken: String): Boolean {
    println("DEBUG: startServerPlatform called - desktop app doesn't need embedded server")
    // Desktop app works standalone, no embedded server needed
    return true
}

actual fun stopServerPlatform(viewModel: DebugForgeViewModel) {
    println("DEBUG: stopServerPlatform called - no server to stop")
}

actual fun isServerRunningPlatform(viewModel: DebugForgeViewModel): Boolean {
    println("DEBUG: isServerRunningPlatform called - desktop app is always 'running'")
    return true
}
