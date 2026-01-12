package com.kmpforge.debugforge.core

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
            _loadingState.value = LoadingState.InProgress("Detecting modules", 0.2f, null)
            val modules = detectModules(path, files)
            
            // Parse root build configuration
            val rootBuildConfig = parseRootBuildConfig(path)
            
            // Collect source files from all modules
            _loadingState.value = LoadingState.InProgress("Parsing source files", 0.4f, null)
            val kotlinFiles = mutableListOf<SourceFile>()
            val resourceFiles = mutableListOf<ResourceFile>()
            
            modules.forEach { module ->
                module.sourceSets.forEach { sourceSet ->
                    sourceSet.files.forEach { file ->
                        processedFiles++
                        _loadingState.value = LoadingState.InProgress(
                            "Parsing source files",
                            0.4f + (0.5f * processedFiles / totalFiles),
                            file.relativePath
                        )
                        kotlinFiles.add(file)
                    }
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
                kotlinFiles = kotlinFiles,
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
            
            gitOperations.clone(url, targetPath, branch) { progress ->
                _loadingState.value = LoadingState.InProgress("Cloning repository", progress, url)
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
        
        val hasGradle = fileSystem.exists(fileSystem.resolvePath(path, "settings.gradle.kts")) ||
                       fileSystem.exists(fileSystem.resolvePath(path, "settings.gradle"))
        
        if (!hasGradle) {
            issues.add("No Gradle settings file found")
            suggestions.add("This tool requires a Gradle-based KMP project")
        }
        
        val rootBuildFile = fileSystem.resolvePath(path, "build.gradle.kts")
        val hasKotlin = if (fileSystem.exists(rootBuildFile)) {
            val content = fileSystem.readFile(rootBuildFile)
            content.contains("kotlin") || content.contains("KotlinMultiplatform")
        } else false
        
        if (!hasKotlin) {
            issues.add("Kotlin plugin not detected in root build file")
        }
        
        // Check for KMP indicators
        val isKmpProject = fileSystem.walkDirectory(path).any { file ->
            if (file.endsWith("build.gradle.kts") || file.endsWith("build.gradle")) {
                val content = fileSystem.readFile(file)  // file is already absolute path
                content.contains("kotlin(\"multiplatform\")") ||
                content.contains("id(\"org.jetbrains.kotlin.multiplatform\")") ||
                content.contains("KotlinMultiplatform")
            } else false
        }
        
        if (!isKmpProject) {
            issues.add("No Kotlin Multiplatform modules detected")
            suggestions.add("Ensure at least one module applies the Kotlin Multiplatform plugin")
        }
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            isKmpProject = isKmpProject,
            hasGradle = hasGradle,
            hasKotlin = hasKotlin,
            issues = issues,
            suggestions = suggestions
        )
    }
    
    private suspend fun detectModules(rootPath: String, files: List<String>): List<DetectedModule> {
        val buildFiles = files.filter { 
            it.endsWith("build.gradle.kts") || it.endsWith("build.gradle") 
        }
        
        return buildFiles.mapNotNull { buildFile ->
            val absoluteModulePath = fileSystem.getParent(buildFile) ?: ""
            // Convert absolute path to relative path from rootPath
            val normalizedRoot = rootPath.replace("\\", "/").trimEnd('/')
            val normalizedModule = absoluteModulePath.replace("\\", "/").trimEnd('/')
            val relativeModulePath = if (normalizedModule.startsWith(normalizedRoot)) {
                normalizedModule.removePrefix(normalizedRoot).trimStart('/')
            } else {
                normalizedModule
            }
            
            // buildFile is already an absolute path from walkDirectory
            val buildContent = fileSystem.readFile(buildFile)
            
            // Only include KMP modules
            if (!isKmpModule(buildContent)) return@mapNotNull null
            
            val gradlePath = computeGradlePath(relativeModulePath)
            val sourceSets = detectSourceSets(rootPath, relativeModulePath, gradlePath)
            
            DetectedModule(
                path = absoluteModulePath,
                gradlePath = gradlePath,
                name = fileSystem.getFileName(absoluteModulePath).ifEmpty { "root" },
                buildFilePath = buildFile,
                buildFileType = if (buildFile.endsWith(".kts")) BuildSystem.GRADLE_KTS else BuildSystem.GRADLE_GROOVY,
                sourceSets = sourceSets
            )
        }
    }
    
    private fun isKmpModule(buildContent: String): Boolean {
        return buildContent.contains("kotlin(\"multiplatform\")") ||
               buildContent.contains("id(\"org.jetbrains.kotlin.multiplatform\")") ||
               buildContent.contains("plugins {") && buildContent.contains("multiplatform")
    }
    
    private suspend fun detectSourceSets(
        rootPath: String, 
        modulePath: String,
        gradlePath: String
    ): List<DetectedSourceSet> {
        val sourceSets = mutableListOf<DetectedSourceSet>()
        val srcPath = if (modulePath.isEmpty()) "src" else "$modulePath/src"
        val fullSrcPath = fileSystem.resolvePath(rootPath, srcPath)
        
        if (!fileSystem.exists(fullSrcPath)) return emptyList()
        
        val sourceSetDirs = fileSystem.listDirectory(fullSrcPath)
        
        sourceSetDirs.forEach { sourceSetName ->
            val platform = detectPlatformFromSourceSetName(sourceSetName)
            val kotlinPath = "$srcPath/$sourceSetName/kotlin"
            val javaPath = "$srcPath/$sourceSetName/java"
            val resourcePath = "$srcPath/$sourceSetName/resources"
            
            val kotlinFiles = if (fileSystem.exists(fileSystem.resolvePath(rootPath, kotlinPath))) {
                collectKotlinFiles(rootPath, kotlinPath, gradlePath, sourceSetName)
            } else emptyList()
            
            sourceSets.add(
                DetectedSourceSet(
                    name = sourceSetName,
                    platform = platform,
                    kotlinPath = if (fileSystem.exists(fileSystem.resolvePath(rootPath, kotlinPath))) kotlinPath else null,
                    javaPath = if (fileSystem.exists(fileSystem.resolvePath(rootPath, javaPath))) javaPath else null,
                    resourcePath = if (fileSystem.exists(fileSystem.resolvePath(rootPath, resourcePath))) resourcePath else null,
                    files = kotlinFiles
                )
            )
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
        return fileSystem.walkDirectory(fullPath)
            .filter { it.endsWith(".kt") || it.endsWith(".kts") }
            .map { absolutePath ->
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
        return ":" + modulePath.replace("/", ":").replace("\\", ":")
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
    
    companion object {
        private const val REFRESH_WINDOW_MS = 60_000L // 1 minute
    }
}

/**
 * Exception thrown during repository operations.
 */
class RepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)
