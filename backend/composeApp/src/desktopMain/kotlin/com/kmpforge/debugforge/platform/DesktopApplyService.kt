package com.kmpforge.debugforge.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop-only service for applying fixes with compilation verification
 */
class DesktopApplyService(private val projectPath: String) {
    
    suspend fun applyWithVerification(
        filePath: String,
        newContent: String
    ): ApplyResult = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val backup = file.readText()
        
        try {
            // Step 1: Apply the change
            file.writeText(newContent)
            
            // Step 2: Run Gradle build
            val buildResult = runGradleBuild()
            
            if (buildResult.success) {
                ApplyResult.Success("Applied and verified successfully")
            } else {
                // Step 3: Rollback on failure
                file.writeText(backup)
                ApplyResult.Failed("Build failed after applying change. Rolled back.\nError: ${buildResult.error}")
            }
        } catch (e: Exception) {
            // Rollback on any error
            try {
                file.writeText(backup)
            } catch (rollbackError: Exception) {
                return@withContext ApplyResult.Failed("Apply failed AND rollback failed: ${e.message}")
            }
            ApplyResult.Failed("Apply failed: ${e.message}")
        }
    }
    
    private fun runGradleBuild(): BuildResult {
        return try {
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            val gradleCommand = if (isWindows) ".\\gradlew.bat" else "./gradlew"
            
            val process = ProcessBuilder()
                .command(gradleCommand, "build", "--console=plain")
                .directory(File(projectPath))
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                BuildResult(success = true, output = output)
            } else {
                BuildResult(success = false, error = output)
            }
        } catch (e: Exception) {
            BuildResult(success = false, error = e.message ?: "Unknown error")
        }
    }
    
    data class BuildResult(
        val success: Boolean,
        val output: String = "",
        val error: String = ""
    )
}

sealed class ApplyResult {
    data class Success(val message: String) : ApplyResult()
    data class Failed(val error: String) : ApplyResult()
}
