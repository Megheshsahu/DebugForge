package com.kmpforge.debugforge.preview

import com.kmpforge.debugforge.core.SourceSetPlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * Default factory for creating platform-specific preview runners.
 * 
 * Stub implementation that returns StubPreviewRunner for all platforms.
 * TODO: Implement actual platform-specific runners.
 */
class DefaultPreviewRunnerFactory : PreviewRunnerFactory {
    override fun createRunner(platform: SourceSetPlatform): PreviewRunner {
        return StubPreviewRunner(platform)
    }
}

/**
 * Stub preview runner that provides placeholder functionality.
 * 
 * Used for initial development and testing.
 * TODO: Implement actual preview rendering per platform.
 */
class StubPreviewRunner(
    private val platform: SourceSetPlatform
) : PreviewRunner {
    
    private val _renderOutputs = MutableStateFlow<PreviewOutput>(createEmptyOutput())
    override val renderOutputs: Flow<PreviewOutput> = _renderOutputs.asStateFlow()
    
    override suspend fun buildPreviewModule(preview: PreviewableComposable) {
        // Stub implementation - no actual build
    }
    
    override suspend fun startRendering(preview: PreviewableComposable) {
        // Stub implementation - emit an empty output
        _renderOutputs.value = createEmptyOutput()
    }
    
    override suspend fun stopRendering() {
        // Stub implementation - nothing to stop
    }
    
    override suspend fun hotReload(preview: PreviewableComposable) {
        // Stub implementation - just re-emit output
        _renderOutputs.value = createEmptyOutput()
    }
    
    override suspend fun captureOutput(): PreviewOutput {
        return _renderOutputs.value
    }
    
    override fun dispose() {
        // Stub implementation - nothing to dispose
    }
    
    private fun createEmptyOutput(): PreviewOutput {
        return PreviewOutput(
            renderType = RenderType.PNG_IMAGE,
            width = 0,
            height = 0,
            contentHash = "stub-${Clock.System.now().toEpochMilliseconds()}",
            imageData = null,
            htmlContent = null,
            timestamp = Clock.System.now().toEpochMilliseconds()
        )
    }
}
