
package com.kmpforge.debugforge.app

import com.kmpforge.debugforge.server.DebugForgeEmbeddedServer
import com.kmpforge.debugforge.app.DebugForgeViewModel


object DesktopServerController {
    private var embeddedServer: DebugForgeEmbeddedServer? = null
    private var viewModel: DebugForgeViewModel? = null

    fun startServer(viewModel: DebugForgeViewModel, groqApiKey: String, githubToken: String): Boolean {
        if (embeddedServer != null) return true // Already running
        this.viewModel = viewModel
        try {
            println("DEBUG: Creating embedded server...")
            embeddedServer = DebugForgeEmbeddedServer(groqApiKey = groqApiKey, githubToken = githubToken)
            println("DEBUG: Starting embedded server...")
            embeddedServer?.start()
            println("DEBUG: Embedded server start() called")
            return true
        } catch (e: Exception) {
            println("DEBUG: Failed to start server: ${e.message}")
            e.printStackTrace()
            viewModel.setServerRunning(false)
            return false
        }
    }

    fun stopServer() {
        embeddedServer?.stop()
        embeddedServer = null
        viewModel?.setServerRunning(false)
        viewModel = null
    }

    fun isServerRunning(): Boolean {
        val serverRunning = embeddedServer?.isRunning() == true
        println("DEBUG: isServerRunning called, embeddedServer.isRunning() = $serverRunning")
        return serverRunning
    }
}
