package com.kmpforge.debugforge.preview

import kotlinx.serialization.Serializable

/**
 * State of preview sessions across all platforms.
 */
@Serializable
sealed class PreviewState {
    /** No active preview session */
    @Serializable
    data object Inactive : PreviewState()
    
    /** Starting preview session */
    @Serializable
    data class Starting(
        val targetPlatforms: Set<PreviewPlatform>,
        val progress: Float,
        val currentStep: String
    ) : PreviewState()
    
    /** Preview session is active */
    @Serializable
    data class Active(
        val sessionId: String,
        val platforms: List<PlatformPreview>,
        val startedAt: Long,
        val diagnosticOverlayEnabled: Boolean,
        val hotReloadEnabled: Boolean
    ) : PreviewState()
    
    /** Preview session failed to start */
    @Serializable
    data class Failed(
        val error: String,
        val platform: PreviewPlatform?,
        val recoveryAction: String?
    ) : PreviewState()
}

/**
 * Preview state for a single platform.
 */
@Serializable
data class PlatformPreview(
    val platform: PreviewPlatform,
    val status: PlatformPreviewStatus,
    val deviceInfo: DeviceInfo?,
    val lastRenderTime: Long?,
    val frameRate: Float?,
    val memoryUsage: Long?,
    val errors: List<PreviewError>
)

@Serializable
enum class PreviewPlatform {
    ANDROID_EMULATOR,
    ANDROID_DEVICE,
    IOS_SIMULATOR,
    IOS_DEVICE,
    DESKTOP_JVM,
    WEB_BROWSER,
    WASM_BROWSER
}

@Serializable
enum class PlatformPreviewStatus {
    STARTING,
    COMPILING,
    DEPLOYING,
    RUNNING,
    PAUSED,
    STOPPED,
    ERROR
}

/**
 * Information about the target device/emulator/browser.
 */
@Serializable
data class DeviceInfo(
    val name: String,
    val identifier: String,
    val osVersion: String?,
    val screenWidth: Int?,
    val screenHeight: Int?,
    val density: Float?
)

/**
 * Error that occurred during preview.
 */
@Serializable
data class PreviewError(
    val message: String,
    val stackTrace: String?,
    val timestamp: Long,
    val isRecoverable: Boolean
)

/**
 * Configuration for a preview session.
 */
@Serializable
data class PreviewConfig(
    /** Platforms to preview on */
    val targetPlatforms: Set<PreviewPlatform>,
    
    /** Enable diagnostic overlay in preview */
    val showDiagnosticOverlay: Boolean = true,
    
    /** Enable hot reload */
    val hotReloadEnabled: Boolean = true,
    
    /** Composable to preview (fully qualified name) */
    val composableTarget: String?,
    
    /** Preview parameters */
    val parameters: Map<String, String> = emptyMap(),
    
    /** Device configurations per platform */
    val deviceConfigs: Map<PreviewPlatform, DeviceConfig> = emptyMap()
)

/**
 * Device configuration for preview.
 */
@Serializable
data class DeviceConfig(
    val deviceId: String?,
    val screenWidth: Int?,
    val screenHeight: Int?,
    val density: Float?,
    val darkMode: Boolean = false,
    val locale: String = "en_US"
)
