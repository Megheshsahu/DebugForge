package com.kmpforge.debugforge.core

import com.kmpforge.debugforge.utils.DebugForgeLogger

/**
 * Interface for detecting modules in different project types.
 */
interface ModuleDetector {
    val supportedBuildSystems: Set<BuildSystem>
    val name: String

    suspend fun detectModules(
        rootPath: String,
        files: List<String>,
        fileSystem: FileSystem
    ): List<DetectedModule>
}

/**
 * Factory for creating appropriate module detectors based on project type.
 */
class ModuleDetectorFactory {
    private val detectors = listOf(
        GradleModuleDetector(),
        MavenModuleDetector(),
        NpmModuleDetector(),
        PythonModuleDetector(),
        RustModuleDetector(),
        GoModuleDetector(),
        CppModuleDetector(),
        CSharpModuleDetector(),
        SwiftModuleDetector(),
        GenericModuleDetector() // Fallback detector
    )

    fun getDetector(buildSystem: BuildSystem): ModuleDetector {
        return detectors.find { buildSystem in it.supportedBuildSystems }
            ?: detectors.last() // Return GenericModuleDetector as fallback
    }

    fun getDetectorsForProjectTypes(projectTypes: List<ProjectType>): List<ModuleDetector> {
        return projectTypes.map { getDetector(it.buildSystem) }.distinct()
    }
}

/**
 * Gradle module detector for both Groovy and Kotlin DSL.
 */
class GradleModuleDetector : ModuleDetector {
    override val supportedBuildSystems = setOf(BuildSystem.GRADLE_GROOVY, BuildSystem.GRADLE_KTS)
    override val name = "Gradle"

    override suspend fun detectModules(
        rootPath: String,
        files: List<String>,
        fileSystem: FileSystem
    ): List<DetectedModule> {
        val buildFiles = files.filter { file ->
            file.endsWith("build.gradle.kts") || file.endsWith("build.gradle")
        }

        return buildFiles.mapNotNull { buildFile ->
            try {
                val absoluteModulePath = fileSystem.getParent(buildFile) ?: ""
                val relativeModulePath = getRelativePath(rootPath, absoluteModulePath)
                val buildContent = fileSystem.readFile(buildFile)

                // Check if this is a Kotlin module
                val hasKotlinPlugin = hasKotlinPlugin(buildContent)
                val hasKotlinFiles = hasKotlinFilesInModule(files, absoluteModulePath)

                if (!hasKotlinPlugin && !hasKotlinFiles) return@mapNotNull null

                val gradlePath = computeGradlePath(relativeModulePath)
                val sourceSets = detectSourceSets(rootPath, relativeModulePath, gradlePath, fileSystem)

                DetectedModule(
                    path = absoluteModulePath,
                    gradlePath = gradlePath,
                    name = fileSystem.getFileName(absoluteModulePath).ifEmpty { "root" },
                    buildFilePath = buildFile,
                    buildFileType = if (buildFile.endsWith(".kts")) BuildSystem.GRADLE_KTS else BuildSystem.GRADLE_GROOVY,
                    sourceSets = sourceSets
                )
            } catch (e: Exception) {
                DebugForgeLogger.error("GradleModuleDetector", "Error detecting module for $buildFile: ${e.message}", e)
                null
            }
        }
    }

    private fun hasKotlinPlugin(buildContent: String): Boolean {
        return buildContent.contains("kotlin(") ||
               buildContent.contains("id(\"org.jetbrains.kotlin.") ||
               buildContent.contains("kotlin-multiplatform") ||
               buildContent.contains("kotlin-jvm") ||
               buildContent.contains("kotlin-js")
    }

    private fun hasKotlinFilesInModule(files: List<String>, modulePath: String): Boolean {
        return files.any { file ->
            file.startsWith(modulePath) && (file.endsWith(".kt") || file.endsWith(".kts"))
        }
    }

    private fun detectSourceSets(
        rootPath: String,
        relativeModulePath: String,
        gradlePath: String,
        fileSystem: FileSystem
    ): List<DetectedSourceSet> {
        // This is a simplified version - in practice, you'd parse the build file
        // For now, return common source sets
        return listOf(
            DetectedSourceSet(
                name = "main",
                platform = SourceSetPlatform.JVM,
                kotlinPath = "src/main/kotlin",
                javaPath = "src/main/java",
                resourcePath = "src/main/resources",
                files = emptyList() // Would be populated by file scanning
            ),
            DetectedSourceSet(
                name = "test",
                platform = SourceSetPlatform.JVM,
                kotlinPath = "src/test/kotlin",
                javaPath = "src/test/java",
                resourcePath = "src/test/resources",
                files = emptyList()
            )
        )
    }
}

/**
 * Maven module detector.
 */
class MavenModuleDetector : ModuleDetector {
    override val supportedBuildSystems = setOf(BuildSystem.MAVEN)
    override val name = "Maven"

    override suspend fun detectModules(
        rootPath: String,
        files: List<String>,
        fileSystem: FileSystem
    ): List<DetectedModule> {
        val pomFiles = files.filter { it.endsWith("pom.xml") }

        return pomFiles.mapNotNull { pomFile ->
            try {
                val absoluteModulePath = fileSystem.getParent(pomFile) ?: ""
                val relativeModulePath = getRelativePath(rootPath, absoluteModulePath)

                val sourceSets = listOf(
                    DetectedSourceSet(
                        name = "main",
                        platform = SourceSetPlatform.JVM,
                        kotlinPath = "src/main/kotlin",
                        javaPath = "src/main/java",
                        resourcePath = "src/main/resources",
                        files = emptyList()
                    ),
                    DetectedSourceSet(
                        name = "test",
                        platform = SourceSetPlatform.JVM,
                        kotlinPath = "src/test/kotlin",
                        javaPath = "src/test/java",
                        resourcePath = "src/test/resources",
                        files = emptyList()
                    )
                )

                DetectedModule(
                    path = absoluteModulePath,
                    gradlePath = computeGradlePath(relativeModulePath), // Reuse gradle path logic
                    name = fileSystem.getFileName(absoluteModulePath).ifEmpty { "root" },
                    buildFilePath = pomFile,
                    buildFileType = BuildSystem.MAVEN,
                    sourceSets = sourceSets
                )
            } catch (e: Exception) {
                DebugForgeLogger.error("MavenModuleDetector", "Error detecting module for $pomFile: ${e.message}", e)
                null
            }
        }
    }
}

/**
 * NPM/Node.js module detector.
 */
class NpmModuleDetector : ModuleDetector {
    override val supportedBuildSystems = setOf(BuildSystem.NPM, BuildSystem.YARN, BuildSystem.PNPM)
    override val name = "NPM"

    override suspend fun detectModules(
        rootPath: String,
        files: List<String>,
        fileSystem: FileSystem
    ): List<DetectedModule> {
        val packageFiles = files.filter { it.endsWith("package.json") }

        return packageFiles.mapNotNull { packageFile ->
            try {
                val absoluteModulePath = fileSystem.getParent(packageFile) ?: ""

                val sourceSets = listOf(
                    DetectedSourceSet(
                        name = "main",
                        platform = SourceSetPlatform.JS,
                        kotlinPath = null, // JS projects might not have Kotlin
                        javaPath = null,
                        resourcePath = "public",
                        files = emptyList()
                    )
                )

                DetectedModule(
                    path = absoluteModulePath,
                    gradlePath = ":", // Root module
                    name = fileSystem.getFileName(absoluteModulePath).ifEmpty { "root" },
                    buildFilePath = packageFile,
                    buildFileType = BuildSystem.NPM,
                    sourceSets = sourceSets
                )
            } catch (e: Exception) {
                DebugForgeLogger.error("NpmModuleDetector", "Error detecting module for $packageFile: ${e.message}", e)
                null
            }
        }
    }
}

/**
 * Python module detector.
 */
class PythonModuleDetector : ModuleDetector {
    override val supportedBuildSystems = setOf(BuildSystem.PIP, BuildSystem.POETRY, BuildSystem.SETUP_PY, BuildSystem.PYPROJECT_TOML)
    override val name = "Python"

    override suspend fun detectModules(
        rootPath: String,
        files: List<String>,
        fileSystem: FileSystem
    ): List<DetectedModule> {
        // For Python, we typically have one main module
        val hasPythonFiles = files.any { it.endsWith(".py") }

        if (!hasPythonFiles) return emptyList()

        val buildFiles = files.filter { file ->
            file.endsWith("setup.py") || file.endsWith("pyproject.toml") ||
            file.endsWith("requirements.txt") || file.endsWith("Pipfile")
        }

        val modulePath = if (buildFiles.isNotEmpty()) {
            fileSystem.getParent(buildFiles.first()) ?: rootPath
        } else {
            rootPath
        }

        val sourceSets = listOf(
            DetectedSourceSet(
                name = "main",
                platform = SourceSetPlatform.UNKNOWN, // Python doesn't have platforms like KMP
                kotlinPath = null,
                javaPath = null,
                resourcePath = null,
                files = emptyList()
            )
        )

        return listOf(
            DetectedModule(
                path = modulePath,
                gradlePath = ":",
                name = "root",
                buildFilePath = buildFiles.firstOrNull() ?: "",
                buildFileType = BuildSystem.PIP,
                sourceSets = sourceSets
            )
        )
    }
}

/**
 * Rust module detector.
 */
class RustModuleDetector : ModuleDetector {
    override val supportedBuildSystems = setOf(BuildSystem.CARGO)
    override val name = "Cargo"

    override suspend fun detectModules(
        rootPath: String,
        files: List<String>,
        fileSystem: FileSystem
    ): List<DetectedModule> {
        val cargoFiles = files.filter { it.endsWith("Cargo.toml") }

        return cargoFiles.mapNotNull { cargoFile ->
            try {
                val absoluteModulePath = fileSystem.getParent(cargoFile) ?: ""

                val sourceSets = listOf(
                    DetectedSourceSet(
                        name = "main",
                        platform = SourceSetPlatform.UNKNOWN,
                        kotlinPath = null,
                        javaPath = null,
                        resourcePath = null,
                        files = emptyList()
                    )
                )

                DetectedModule(
                    path = absoluteModulePath,
                    gradlePath = ":",
                    name = fileSystem.getFileName(absoluteModulePath).ifEmpty { "root" },
                    buildFilePath = cargoFile,
                    buildFileType = BuildSystem.CARGO,
                    sourceSets = sourceSets
                )
            } catch (e: Exception) {
                DebugForgeLogger.error("RustModuleDetector", "Error detecting module for $cargoFile: ${e.message}", e)
                null
            }
        }
    }
}

/**
 * Go module detector.
 */
class GoModuleDetector : ModuleDetector {
    override val supportedBuildSystems = setOf(BuildSystem.GO_MOD)
    override val name = "Go Modules"

    override suspend fun detectModules(
        rootPath: String,
        files: List<String>,
        fileSystem: FileSystem
    ): List<DetectedModule> {
        val goModFiles = files.filter { it.endsWith("go.mod") }

        return goModFiles.mapNotNull { goModFile ->
            try {
                val absoluteModulePath = fileSystem.getParent(goModFile) ?: ""

                val sourceSets = listOf(
                    DetectedSourceSet(
                        name = "main",
                        platform = SourceSetPlatform.UNKNOWN,
                        kotlinPath = null,
                        javaPath = null,
                        resourcePath = null,
                        files = emptyList()
                    )
                )

                DetectedModule(
                    path = absoluteModulePath,
                    gradlePath = ":",
                    name = fileSystem.getFileName(absoluteModulePath).ifEmpty { "root" },
                    buildFilePath = goModFile,
                    buildFileType = BuildSystem.GO_MOD,
                    sourceSets = sourceSets
                )
            } catch (e: Exception) {
                DebugForgeLogger.error("GoModuleDetector", "Error detecting module for $goModFile: ${e.message}", e)
                null
            }
        }
    }
}

/**
 * C/C++ module detector.
 */
class CppModuleDetector : ModuleDetector {
    override val supportedBuildSystems = setOf(BuildSystem.CMAKE, BuildSystem.MAKEFILE)
    override val name = "C/C++"

    override suspend fun detectModules(
        rootPath: String,
        files: List<String>,
        fileSystem: FileSystem
    ): List<DetectedModule> {
        val buildFiles = files.filter { file ->
            file.endsWith("CMakeLists.txt") || file.endsWith("Makefile")
        }

        return buildFiles.mapNotNull { buildFile ->
            try {
                val absoluteModulePath = fileSystem.getParent(buildFile) ?: ""

                val sourceSets = listOf(
                    DetectedSourceSet(
                        name = "main",
                        platform = SourceSetPlatform.UNKNOWN,
                        kotlinPath = null,
                        javaPath = null,
                        resourcePath = null,
                        files = emptyList()
                    )
                )

                DetectedModule(
                    path = absoluteModulePath,
                    gradlePath = ":",
                    name = fileSystem.getFileName(absoluteModulePath).ifEmpty { "root" },
                    buildFilePath = buildFile,
                    buildFileType = if (buildFile.endsWith("CMakeLists.txt")) BuildSystem.CMAKE else BuildSystem.MAKEFILE,
                    sourceSets = sourceSets
                )
            } catch (e: Exception) {
                DebugForgeLogger.error("CppModuleDetector", "Error detecting module for $buildFile: ${e.message}", e)
                null
            }
        }
    }
}

/**
 * C# module detector.
 */
class CSharpModuleDetector : ModuleDetector {
    override val supportedBuildSystems = setOf(BuildSystem.CSPROJ, BuildSystem.SLN)
    override val name = "C#"

    override suspend fun detectModules(
        rootPath: String,
        files: List<String>,
        fileSystem: FileSystem
    ): List<DetectedModule> {
        val projectFiles = files.filter { file ->
            file.endsWith(".csproj") || file.endsWith(".sln")
        }

        return projectFiles.mapNotNull { projectFile ->
            try {
                val absoluteModulePath = fileSystem.getParent(projectFile) ?: ""

                val sourceSets = listOf(
                    DetectedSourceSet(
                        name = "main",
                        platform = SourceSetPlatform.UNKNOWN,
                        kotlinPath = null,
                        javaPath = null,
                        resourcePath = null,
                        files = emptyList()
                    )
                )

                DetectedModule(
                    path = absoluteModulePath,
                    gradlePath = ":",
                    name = fileSystem.getFileName(absoluteModulePath).ifEmpty { "root" },
                    buildFilePath = projectFile,
                    buildFileType = if (projectFile.endsWith(".csproj")) BuildSystem.CSPROJ else BuildSystem.SLN,
                    sourceSets = sourceSets
                )
            } catch (e: Exception) {
                DebugForgeLogger.error("CSharpModuleDetector", "Error detecting module for $projectFile: ${e.message}", e)
                null
            }
        }
    }
}

/**
 * Swift module detector.
 */
class SwiftModuleDetector : ModuleDetector {
    override val supportedBuildSystems = setOf(BuildSystem.XCODEPROJ)
    override val name = "Swift"

    override suspend fun detectModules(
        rootPath: String,
        files: List<String>,
        fileSystem: FileSystem
    ): List<DetectedModule> {
        val projectFiles = files.filter { file ->
            file.endsWith("Package.swift") || file.contains(".xcodeproj")
        }

        return projectFiles.mapNotNull { projectFile ->
            try {
                val absoluteModulePath = fileSystem.getParent(projectFile) ?: ""

                val sourceSets = listOf(
                    DetectedSourceSet(
                        name = "main",
                        platform = SourceSetPlatform.UNKNOWN,
                        kotlinPath = null,
                        javaPath = null,
                        resourcePath = null,
                        files = emptyList()
                    )
                )

                DetectedModule(
                    path = absoluteModulePath,
                    gradlePath = ":",
                    name = fileSystem.getFileName(absoluteModulePath).ifEmpty { "root" },
                    buildFilePath = projectFile,
                    buildFileType = BuildSystem.XCODEPROJ,
                    sourceSets = sourceSets
                )
            } catch (e: Exception) {
                DebugForgeLogger.error("SwiftModuleDetector", "Error detecting module for $projectFile: ${e.message}", e)
                null
            }
        }
    }
}

/**
 * Generic fallback module detector for unknown project types.
 */
class GenericModuleDetector : ModuleDetector {
    override val supportedBuildSystems = setOf(BuildSystem.UNKNOWN)
    override val name = "Generic"

    override suspend fun detectModules(
        rootPath: String,
        files: List<String>,
        fileSystem: FileSystem
    ): List<DetectedModule> {
        // For unknown project types, create a single root module
        val hasSourceFiles = files.any { file ->
            file.endsWith(".kt") || file.endsWith(".java") || file.endsWith(".js") ||
            file.endsWith(".ts") || file.endsWith(".py") || file.endsWith(".rs") ||
            file.endsWith(".go") || file.endsWith(".c") || file.endsWith(".cpp") ||
            file.endsWith(".cs") || file.endsWith(".swift")
        }

        if (!hasSourceFiles) return emptyList()

        val sourceSets = listOf(
            DetectedSourceSet(
                name = "main",
                platform = SourceSetPlatform.UNKNOWN,
                kotlinPath = null,
                javaPath = null,
                resourcePath = null,
                files = emptyList()
            )
        )

        return listOf(
            DetectedModule(
                path = rootPath,
                gradlePath = ":",
                name = "root",
                buildFilePath = "",
                buildFileType = BuildSystem.UNKNOWN,
                sourceSets = sourceSets
            )
        )
    }
}

// Utility functions
private fun getRelativePath(rootPath: String, absolutePath: String): String {
    val normalizedRoot = rootPath.replace("\\", "/").trimEnd('/')
    val normalizedPath = absolutePath.replace("\\", "/").trimEnd('/')
    return if (normalizedPath.startsWith(normalizedRoot)) {
        normalizedPath.removePrefix(normalizedRoot).trimStart('/')
    } else {
        normalizedPath
    }
}

private fun computeGradlePath(relativePath: String): String {
    return if (relativePath.isEmpty()) {
        ":"
    } else {
        ":${relativePath.replace("/", ":")}"
    }
}