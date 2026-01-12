package com.kmpforge.debugforge.app

// No-op actuals for non-JVM platforms (if needed)
actual fun startServerPlatform(viewModel: DebugForgeViewModel, groqApiKey: String, githubToken: String): Boolean = true
actual fun stopServerPlatform(viewModel: DebugForgeViewModel) {}
actual fun isServerRunningPlatform(viewModel: DebugForgeViewModel): Boolean = false
