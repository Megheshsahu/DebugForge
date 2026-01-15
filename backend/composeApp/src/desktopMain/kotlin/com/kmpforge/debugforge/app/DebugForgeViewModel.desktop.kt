package com.kmpforge.debugforge.app

import com.kmpforge.debugforge.utils.DebugForgeLogger

actual fun startServerPlatform(viewModel: DebugForgeViewModel, groqApiKey: String, githubToken: String): Boolean {
    DebugForgeLogger.debug("DebugForgeViewModel", "startServerPlatform called - desktop app doesn't need embedded server")
    // Desktop app works standalone, no embedded server needed
    return true
}

actual fun stopServerPlatform(viewModel: DebugForgeViewModel) {
    DebugForgeLogger.debug("DebugForgeViewModel", "stopServerPlatform called - no server to stop")
}

actual fun isServerRunningPlatform(viewModel: DebugForgeViewModel): Boolean {
    DebugForgeLogger.debug("DebugForgeViewModel", "isServerRunningPlatform called - desktop app is always 'running'")
    return true
}
