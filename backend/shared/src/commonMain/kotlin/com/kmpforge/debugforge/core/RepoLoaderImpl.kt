package com.kmpforge.debugforge.core

import com.kmpforge.debugforge.utils.DebugForgeLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * Common implementation of RepoLoader that handles cross-platform parsing logic.
 * Platform-specific operations are delegated to FileSystem and GitOperations interfaces.
 */
class RepoLoaderImpl(
    private val fileSystem: FileSystem,
    private val gitOperations: GitOperations
) : RepoLoader {
    
    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    override val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    private val projectTypeDetector = ProjectTypeDetector()
    private val moduleDetectorFactory = ModuleDetectorFactory()
    
    override suspend fun loadLocalRepository(path: String): Result<ParsedRepository> {
        return try {
            _loadingState.value = LoadingState.InProgress("Validating repository", 0f, null)
            
            val validation = validateRepository(path)
            if (!validation.isValid) {
                return Result.failure(
                    RepositoryException("Invalid repository: ${validation.issues.joinToString()}")
                )
            }
            
            _loadingState.value = LoadingState.InProgress("Scanning files", 0.1f, null)
            
            val files = fileSystem.walkDirectory(path)
            val totalFiles = files.size
            var processedFiles = 0
            
            // Parse build files to detect modules
            _loadingState.value = LoadingState.InProgress("Detecting project types", 0.15f, null)
            val projectTypes = projectTypeDetector.detectProjectTypes(files)
            val primaryProjectType = projectTypeDetector.getPrimaryProjectType(projectTypes)

            _loadingState.value = LoadingState.InProgress("Detecting modules", 0.2f, null)
            val modules = if (primaryProjectType != null) {
                val detector = moduleDetectorFactory.getDetector(primaryProjectType.buildSystem)
                detector.detectModules(path, files, fileSystem)
            } else {
                // Fallback to generic detection
                val genericDetector = moduleDetectorFactory.getDetector(BuildSystem.UNKNOWN)
                genericDetector.detectModules(path, files, fileSystem)
            }

            DebugForgeLogger.debug("RepoLoader", "Detected ${modules.size} modules using ${primaryProjectType?.name ?: "generic"} detector")

            // Parse root build configuration
            val rootBuildConfig = parseRootBuildConfig(path)
            
            // Collect source files from all modules
            _loadingState.value = LoadingState.InProgress("Parsing source files", 0.4f, null)
            val sourceFiles = mutableListOf<SourceFile>()
            val resourceFiles = mutableListOf<ResourceFile>()

            if (modules.isNotEmpty()) {
                modules.forEach { module ->
                    module.sourceSets.forEach { sourceSet ->
                        sourceSet.files.forEach { file ->
                            processedFiles++
                            _loadingState.value = LoadingState.InProgress(
                                "Parsing source files",
                                0.4f + (0.5f * processedFiles / totalFiles),
                                file.relativePath
                            )
                            sourceFiles.add(file)
                        }
                    }
                }
            } else {
                // Fallback: collect all source files in the project if no modules detected
                files.filter { isSourceFile(it) }
                    .filter { !it.contains("/build/") && !it.contains("\\build\\") && !it.contains("/node_modules/") && !it.contains("\\node_modules\\") }
                    .forEach { sourceFile ->
                        processedFiles++
                        _loadingState.value = LoadingState.InProgress(
                            "Parsing source files",
                            0.4f + (0.5f * processedFiles / totalFiles),
                            sourceFile.removePrefix(path).removePrefix("/").removePrefix("\\")
                        )

                        val content = fileSystem.readFile(sourceFile)
                        sourceFiles.add(
                            SourceFile(
                                absolutePath = sourceFile,
                                relativePath = sourceFile.removePrefix(path).removePrefix("/").removePrefix("\\"),
                                moduleGradlePath = ":",
                                sourceSetName = "main",
                                packageName = extractPackageName(content),
                                content = content,
                                hash = fileSystem.computeFileHash(sourceFile),
                                lastModified = fileSystem.getLastModified(sourceFile),
                                lineCount = content.lines().size
                            )
                        )
                    }
            }
            
            // Collect resource files (files are already absolute paths)
            files.filter { isResourceFile(it) }.forEach { resourcePath ->
                val moduleInfo = findModuleForFile(resourcePath, modules)
                if (moduleInfo != null) {
                    resourceFiles.add(
                        ResourceFile(
                            absolutePath = resourcePath,
                            relativePath = resourcePath.removePrefix(path).removePrefix("/"),
                            moduleGradlePath = moduleInfo.first,
                            sourceSetName = moduleInfo.second,
                            type = detectResourceType(resourcePath)
                        )
                    )
                }
            }
            
            // Get Git info if available
            _loadingState.value = LoadingState.InProgress("Reading Git information", 0.95f, null)
            val gitInfo = buildGitInfo(path)
            
            val repository = ParsedRepository(
                rootPath = path,
                name = fileSystem.getFileName(path),
                modules = modules,
                rootBuildConfig = rootBuildConfig,
                sourceFiles = sourceFiles,
                resourceFiles = resourceFiles,
                gitInfo = gitInfo,
                parsedAt = Clock.System.now().toEpochMilliseconds()
            )
            
            _loadingState.value = LoadingState.Completed(path)
            Result.success(repository)
            
        } catch (e: Exception) {
            _loadingState.value = LoadingState.Error(e.message ?: "Unknown error", e)
            Result.failure(e)
        }
    }
    
    override suspend fun cloneAndLoad(
        url: String,
        targetPath: String,
        branch: String?
    ): Result<ParsedRepository> {
        return try {
            _loadingState.value = LoadingState.InProgress("Cloning repository", 0f, url)
            
            val cloneResult = gitOperations.clone(url, targetPath, branch) { progress ->
                _loadingState.value = LoadingState.InProgress("Cloning repository", progress, url)
            }
            
            if (cloneResult.isFailure) {
                val error = cloneResult.exceptionOrNull() ?: Exception("Unknown clone error")
                _loadingState.value = LoadingState.Error("Clone failed: ${error.message}", error)
                return Result.failure(error)
            }
            
            loadLocalRepository(targetPath)
            
        } catch (e: Exception) {
            _loadingState.value = LoadingState.Error("Clone failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun refresh(repoPath: String): Result<RepositoryDelta> {
        return try {
            val currentFiles = fileSystem.walkDirectory(repoPath)
            // currentFiles already contains absolute paths
            val currentHashes = currentFiles.associateWith { 
                fileSystem.computeFileHash(it) 
            }
            
            // Compare with stored hashes (from previous load)
            // For now, we detect changes based on modification time
            val currentTimeMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            val changedFiles = currentFiles.filter { file ->
                fileSystem.getLastModified(file) > currentTimeMs - REFRESH_WINDOW_MS
            }
            
            Result.success(
                RepositoryDelta(
                    addedFiles = emptyList(), // Would need previous state to compute
                    modifiedFiles = changedFiles,
                    deletedFiles = emptyList(),
                    hasStructuralChanges = changedFiles.any { 
                        it.endsWith("build.gradle.kts") || it.endsWith("build.gradle") 
                    }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun validateRepository(path: String): ValidationResult {
        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        // Look for any Gradle files in the project (more flexible)
        val allFiles = fileSystem.walkDirectory(path)
        val hasAnyGradleFiles = allFiles.any {
            it.endsWith("build.gradle.kts") || it.endsWith("build.gradle") ||
            it.endsWith("settings.gradle.kts") || it.endsWith("settings.gradle")
        }

        if (!hasAnyGradleFiles) {
            issues.add("No Gradle build files found")
            suggestions.add("This tool works best with Gradle-based projects. Ensure you have build.gradle.kts or build.gradle files.")
        }

        // Check for Kotlin plugins in any build file (more flexible)
        val hasKotlinPlugin = allFiles.any { file ->
            if (file.endsWith("build.gradle.kts") || file.endsWith("build.gradle")) {
                val content = fileSystem.readFile(file)
                isKotlinModule(content)
            } else false
        }

        if (!hasKotlinPlugin) {
            issues.add("No Kotlin plugins detected in build files")
            suggestions.add("This tool specializes in Kotlin projects. Ensure at least one build file applies a Kotlin plugin (kotlin-jvm, kotlin-multiplatform, etc.)")
        }

        // Check for Kotlin source files as a fallback
        val hasKotlinFiles = allFiles.any { it.endsWith(".kt") || it.endsWith(".kts") }

        if (!hasKotlinFiles && !hasKotlinPlugin) {
            issues.add("No Kotlin source files found")
            suggestions.add("Ensure your project contains Kotlin source files (.kt or .kts)")
        }

        // More lenient validation - allow projects that have some Kotlin indicators
        val isValid = hasAnyGradleFiles && (hasKotlinPlugin || hasKotlinFiles)

        return ValidationResult(
            isValid = isValid,
            isKotlinProject = hasKotlinPlugin || hasKotlinFiles,
            hasGradle = hasAnyGradleFiles,
            hasKotlin = hasKotlinPlugin,
            issues = issues,
            suggestions = suggestions
        )
    }
    
    private suspend fun detectModules(rootPath: String, files: List<String>): List<DetectedModule> {
        val buildFiles = files.filter { file ->
            file.endsWith("build.gradle.kts") || file.endsWith("build.gradle")
        }

        val modules = buildFiles.mapNotNull { buildFile ->
            val absoluteModulePath = fileSystem.getParent(buildFile) ?: ""
            // Convert absolute path to relative path from rootPath
            val normalizedRoot = rootPath.replace("\\", "/").replace("//", "/").trimEnd('/')
            val normalizedModule = absoluteModulePath.replace("\\", "/").trimEnd('/')
            val relativeModulePath = if (normalizedModule.startsWith(normalizedRoot)) {
                normalizedModule.removePrefix(normalizedRoot).trimStart('/')
            } else {
                // If the module path doesn't start with root path, it might be the root itself
                if (normalizedModule == normalizedRoot) "" else normalizedModule
            }

            // buildFile is already an absolute path from walkDirectory
            val buildContent = fileSystem.readFile(buildFile)

            // Include modules that have Kotlin plugins OR Kotlin source files
            val hasKotlinPlugin = isKotlinModule(buildContent)
            val hasKotlinFiles = files.any { file ->
                file.startsWith(absoluteModulePath) && (file.endsWith(".kt") || file.endsWith(".kts"))
            }

            if (!hasKotlinPlugin && !hasKotlinFiles) return@mapNotNull null

            val gradlePath = computeGradlePath(relativeModulePath)
            val sourceSets = try {
                detectSourceSets(rootPath, relativeModulePath, gradlePath)
            } catch (e: Exception) {
                emptyList()
            }

            DetectedModule(
                path = absoluteModulePath,
                gradlePath = gradlePath,
                name = fileSystem.getFileName(absoluteModulePath).ifEmpty { "root" },
                buildFilePath = buildFile,
                buildFileType = if (buildFile.endsWith(".kts")) BuildSystem.GRADLE_KTS else BuildSystem.GRADLE_GROOVY,
                sourceSets = sourceSets
            )
        }

        DebugForgeLogger.debug("RepoLoader", "detectModules - returning ${modules.size} modules")

        // If no modules found but we have Kotlin files, create a root module
        if (modules.isEmpty()) {
            val hasKotlinFiles = files.any { it.endsWith(".kt") || it.endsWith(".kts") }
            if (hasKotlinFiles) {
                // Try to find a root build file
                val rootBuildFile = files.find { file ->
                    val parent = fileSystem.getParent(file) ?: ""
                    parent == rootPath && (file.endsWith("build.gradle.kts") || file.endsWith("build.gradle"))
                }

                if (rootBuildFile != null) {
                    val gradlePath = ":"
                    val sourceSets = detectSourceSets(rootPath, "", gradlePath)

                    return listOf(DetectedModule(
                        path = rootPath,
                        gradlePath = gradlePath,
                        name = "root",
                        buildFilePath = rootBuildFile,
                        buildFileType = if (rootBuildFile.endsWith(".kts")) BuildSystem.GRADLE_KTS else BuildSystem.GRADLE_GROOVY,
                        sourceSets = sourceSets
                    ))
                }
            }
        }

        return modules
    }
    
    private fun isKotlinModule(buildContent: String): Boolean {
        return buildContent.contains("kotlin(") ||
               buildContent.contains("id(\"org.jetbrains.kotlin.") ||
               buildContent.contains("kotlin-multiplatform") ||
               buildContent.contains("kotlin-jvm") ||
               buildContent.contains("kotlin-js") ||
               buildContent.contains("kotlin-android") ||
               (buildContent.contains("plugins") && buildContent.contains("kotlin"))
    }
    
    private suspend fun detectSourceSets(
        rootPath: String,
        modulePath: String,
        gradlePath: String
    ): List<DetectedSourceSet> {
        val sourceSets = mutableListOf<DetectedSourceSet>()

        // Ensure modulePath is relative to rootPath
        val normalizedRoot = rootPath.replace("\\", "/").trimEnd('/')
        val normalizedModule = modulePath.replace("\\", "/").trimEnd('/')
        val relativeModulePath = if (normalizedModule.startsWith(normalizedRoot)) {
            normalizedModule.removePrefix(normalizedRoot).trimStart('/')
        } else if (normalizedModule.contains(":/") || normalizedModule.startsWith("/")) {
            // Absolute path - this shouldn't happen, but handle it
            return emptyList()
        } else {
            normalizedModule
        }

        val moduleFullPath = if (modulePath.isEmpty()) rootPath else fileSystem.resolvePath(rootPath, modulePath)

        // Try to detect standard KMP source set structure
        val srcPath = fileSystem.resolvePath(moduleFullPath, "src")
        if (fileSystem.exists(srcPath) && fileSystem.isDirectory(srcPath)) {
            val srcContents = fileSystem.listDirectory(srcPath)
            val sourceSetDirs = srcContents.filter { fileSystem.isDirectory(fileSystem.resolvePath(srcPath, it)) }

            for (sourceSetDir in sourceSetDirs) {
                val kotlinPath = fileSystem.resolvePath(fileSystem.resolvePath(srcPath, sourceSetDir), "kotlin")
                if (fileSystem.exists(kotlinPath) && fileSystem.isDirectory(kotlinPath)) {
                    // kotlinPath should be relative to rootPath
                    val kotlinPathRelative = if (relativeModulePath.isEmpty()) {
                        "src/$sourceSetDir/kotlin"
                    } else {
                        "$relativeModulePath/src/$sourceSetDir/kotlin"
                    }
                    val kotlinFiles = collectKotlinFiles(rootPath, kotlinPathRelative, gradlePath, sourceSetDir)
                    if (kotlinFiles.isNotEmpty()) {
                        sourceSets.add(
                            DetectedSourceSet(
                                name = sourceSetDir,
                                platform = detectPlatformFromSourceSetName(sourceSetDir),
                                kotlinPath = kotlinPathRelative,
                                javaPath = null,
                                resourcePath = null,
                                files = kotlinFiles
                            )
                        )
                    }
                }
            }
        }

        // If no source sets found with standard detection, use fallback
        if (sourceSets.isEmpty()) {
            val allKotlinFiles = try {
                collectKotlinFilesAnywhere(moduleFullPath, gradlePath)
            } catch (e: Exception) {
                emptyList()
            }

            if (allKotlinFiles.isNotEmpty()) {
                sourceSets.add(
                    DetectedSourceSet(
                        name = "main",
                        platform = SourceSetPlatform.COMMON,
                        kotlinPath = null,
                        javaPath = null,
                        resourcePath = null,
                        files = allKotlinFiles
                    )
                )
            }
        }

        return sourceSets
    }
    
    private suspend fun collectKotlinFiles(
        rootPath: String,
        kotlinPath: String,
        gradlePath: String,
        sourceSetName: String
    ): List<SourceFile> {
        val fullPath = fileSystem.resolvePath(rootPath, kotlinPath)
        // walkDirectory returns absolute paths
        val allFiles = fileSystem.walkDirectory(fullPath)
        DebugForgeLogger.debug("RepoLoader", "collectKotlinFiles - allFiles count: ${allFiles.size}")
        val kotlinFiles = allFiles.filter { it.endsWith(".kt") || it.endsWith(".kts") }
        DebugForgeLogger.debug("RepoLoader", "collectKotlinFiles - kotlinFiles count: ${kotlinFiles.size}, files: ${kotlinFiles.take(3).joinToString()}")
        return kotlinFiles.map { absolutePath ->
                // Compute relative path from the absolute path
                val normalizedFull = fullPath.replace("\\", "/").trimEnd('/')
                val normalizedAbs = absolutePath.replace("\\", "/")
                val fileRelativePath = if (normalizedAbs.startsWith(normalizedFull)) {
                    normalizedAbs.removePrefix(normalizedFull).trimStart('/')
                } else {
                    fileSystem.getFileName(absolutePath)
                }
                
                val content = fileSystem.readFile(absolutePath)
                SourceFile(
                    absolutePath = absolutePath,
                    relativePath = "$kotlinPath/$fileRelativePath",
                    moduleGradlePath = gradlePath,
                    sourceSetName = sourceSetName,
                    packageName = extractPackageName(content),
                    content = content,
                    hash = fileSystem.computeFileHash(absolutePath),
                    lastModified = fileSystem.getLastModified(absolutePath),
                    lineCount = content.lines().size
                )
            }
    }
    
    private suspend fun collectKotlinFilesAnywhere(
        moduleFullPath: String,
        gradlePath: String
    ): List<SourceFile> {
        // walkDirectory returns absolute paths
        val allFiles = fileSystem.walkDirectory(moduleFullPath)
        val kotlinFiles = allFiles.filter { it.endsWith(".kt") || it.endsWith(".kts") }
        return kotlinFiles.map { absolutePath ->
                // Compute relative path from module root
                val normalizedModule = moduleFullPath.replace("\\", "/").trimEnd('/')
                val normalizedAbs = absolutePath.replace("\\", "/")
                val fileRelativePath = if (normalizedAbs.startsWith(normalizedModule)) {
                    normalizedAbs.removePrefix(normalizedModule).trimStart('/')
                } else {
                    fileSystem.getFileName(absolutePath)
                }
                
                val content = fileSystem.readFile(absolutePath)
                SourceFile(
                    absolutePath = absolutePath,
                    relativePath = fileRelativePath,
                    moduleGradlePath = gradlePath,
                    sourceSetName = "main",
                    packageName = extractPackageName(content),
                    content = content,
                    hash = fileSystem.computeFileHash(absolutePath),
                    lastModified = fileSystem.getLastModified(absolutePath),
                    lineCount = content.lines().size
                )
            }
    }
    
    private fun extractPackageName(content: String): String? {
        val packageRegex = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)
        return packageRegex.find(content)?.groupValues?.getOrNull(1)
    }
    
    private fun detectPlatformFromSourceSetName(name: String): SourceSetPlatform {
        return when {
            name.startsWith("common") -> SourceSetPlatform.COMMON
            name.startsWith("android") -> SourceSetPlatform.ANDROID
            name.startsWith("ios") -> SourceSetPlatform.IOS
            name.startsWith("jvm") || name.startsWith("desktop") -> SourceSetPlatform.JVM
            name.startsWith("js") -> SourceSetPlatform.JS
            name.startsWith("wasm") -> SourceSetPlatform.WASM
            name.startsWith("native") -> SourceSetPlatform.NATIVE
            name.startsWith("macos") -> SourceSetPlatform.MACOS
            name.startsWith("linux") -> SourceSetPlatform.LINUX
            name.startsWith("mingw") || name.startsWith("windows") -> SourceSetPlatform.WINDOWS
            else -> SourceSetPlatform.UNKNOWN
        }
    }
    
    private fun computeGradlePath(modulePath: String): String {
        if (modulePath.isEmpty()) return ":"
        // Convert path separators to colons and ensure it starts with :
        val normalizedPath = modulePath.replace("\\", "/").trim('/')
        return ":" + normalizedPath.replace("/", ":")
    }
    
    private suspend fun parseRootBuildConfig(rootPath: String): RootBuildConfig {
        val buildFile = listOf("build.gradle.kts", "build.gradle")
            .map { fileSystem.resolvePath(rootPath, it) }
            .firstOrNull { fileSystem.exists(it) }
        
        val content = buildFile?.let { fileSystem.readFile(it) } ?: ""
        
        // Parse versions from version catalogs or build files
        val kotlinVersion = extractVersion(content, "kotlin") 
            ?: extractVersionFromCatalog(rootPath, "kotlin")
        val agpVersion = extractVersion(content, "com.android.application")
            ?: extractVersionFromCatalog(rootPath, "agp")
        val composeVersion = extractVersion(content, "compose")
            ?: extractVersionFromCatalog(rootPath, "compose")
        
        // Read Gradle version from wrapper
        val gradleVersion = readGradleWrapperVersion(rootPath)
        
        return RootBuildConfig(
            gradleVersion = gradleVersion,
            kotlinVersion = kotlinVersion,
            agpVersion = agpVersion,
            composeVersion = composeVersion,
            plugins = extractPlugins(content),
            repositories = extractRepositories(content)
        )
    }
    
    private fun extractVersion(content: String, identifier: String): String? {
        val patterns = listOf(
            Regex("""$identifier.*version\s*[=:]\s*["']([^"']+)["']"""),
            Regex("""id\s*\(\s*["'].*$identifier.*["']\s*\)\s*version\s*["']([^"']+)["']"""),
            Regex("""$identifier\s*=\s*["']([^"']+)["']""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(content)
            if (match != null) return match.groupValues.getOrNull(1)
        }
        return null
    }
    
    private suspend fun extractVersionFromCatalog(rootPath: String, key: String): String? {
        val catalogPath = fileSystem.resolvePath(rootPath, "gradle/libs.versions.toml")
        if (!fileSystem.exists(catalogPath)) return null
        
        val content = fileSystem.readFile(catalogPath)
        val pattern = Regex("""$key\s*=\s*["']([^"']+)["']""")
        return pattern.find(content)?.groupValues?.getOrNull(1)
    }
    
    private suspend fun readGradleWrapperVersion(rootPath: String): String? {
        val propsPath = fileSystem.resolvePath(rootPath, "gradle/wrapper/gradle-wrapper.properties")
        if (!fileSystem.exists(propsPath)) return null
        
        val content = fileSystem.readFile(propsPath)
        val pattern = Regex("""gradle-(\d+\.\d+(?:\.\d+)?)-""")
        return pattern.find(content)?.groupValues?.getOrNull(1)
    }
    
    private fun extractPlugins(content: String): List<String> {
        val plugins = mutableListOf<String>()
        val pluginPattern = Regex("""id\s*\(\s*["']([^"']+)["']\s*\)""")
        pluginPattern.findAll(content).forEach { 
            plugins.add(it.groupValues[1]) 
        }
        return plugins
    }
    
    private fun extractRepositories(content: String): List<String> {
        val repos = mutableListOf<String>()
        val repoPatterns = listOf(
            Regex("""mavenCentral\s*\(\s*\)"""),
            Regex("""google\s*\(\s*\)"""),
            Regex("""gradlePluginPortal\s*\(\s*\)"""),
            Regex("""maven\s*\(\s*["']([^"']+)["']\s*\)"""),
            Regex("""maven\s*\{\s*url\s*=?\s*["']([^"']+)["']""")
        )
        
        repoPatterns.forEach { pattern ->
            pattern.findAll(content).forEach { match ->
                repos.add(match.value.take(50)) // Truncate long URLs
            }
        }
        return repos
    }
    
    private fun isResourceFile(path: String): Boolean {
        val resourceExtensions = setOf("xml", "json", "properties", "png", "jpg", "jpeg", "webp", "svg", "ttf", "otf")
        return resourceExtensions.any { path.endsWith(".$it") }
    }
    
    private fun detectResourceType(path: String): ResourceType {
        return when {
            path.endsWith(".xml") -> ResourceType.XML
            path.endsWith(".json") -> ResourceType.JSON
            path.endsWith(".properties") -> ResourceType.PROPERTIES
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".webp") -> ResourceType.IMAGE
            path.endsWith(".ttf") || path.endsWith(".otf") -> ResourceType.FONT
            else -> ResourceType.OTHER
        }
    }
    
    private fun findModuleForFile(path: String, modules: List<DetectedModule>): Pair<String, String>? {
        for (module in modules) {
            for (sourceSet in module.sourceSets) {
                if (sourceSet.resourcePath != null && path.startsWith(sourceSet.resourcePath)) {
                    return module.gradlePath to sourceSet.name
                }
            }
        }
        return null
    }
    
    private suspend fun buildGitInfo(path: String): GitInfo? {
        return if (gitOperations.isGitRepository(path)) {
            GitInfo(
                remoteUrl = gitOperations.getRemoteUrl(path),
                currentBranch = gitOperations.getCurrentBranch(path) ?: "unknown",
                headCommit = gitOperations.getCurrentCommitHash(path) ?: "unknown",
                isDirty = false, // TODO: implement isDirty check
                lastCommitMessage = gitOperations.getLastCommitMessage(path),
                lastCommitAuthor = gitOperations.getLastCommitAuthor(path),
                lastCommitTimestamp = gitOperations.getLastCommitDate(path)
            )
        } else {
            null
        }
    }
    
    private fun isSourceFile(filePath: String): Boolean {
        val extensions = listOf(
            ".kt", ".kts", ".java", ".js", ".mjs", ".ts", ".tsx", 
            ".py", ".rs", ".go", ".c", ".cpp", ".h", ".hpp", ".cc", ".cxx",
            ".cs", ".swift", ".scala"
        )
        return extensions.any { filePath.endsWith(it) }
    }
    
    companion object {
        private const val REFRESH_WINDOW_MS = 60_000L // 1 minute
    }
}

/**
 * Exception thrown during repository operations.
 */
class RepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)
