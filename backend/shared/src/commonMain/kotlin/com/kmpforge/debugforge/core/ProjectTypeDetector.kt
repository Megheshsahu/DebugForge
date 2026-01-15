package com.kmpforge.debugforge.core

import com.kmpforge.debugforge.utils.DebugForgeLogger

/**
 * Represents a detected project type with its characteristics.
 */
data class ProjectType(
    val name: String,
    val buildSystem: BuildSystem,
    val primaryLanguage: ProgrammingLanguage,
    val supportedLanguages: Set<ProgrammingLanguage>,
    val buildFiles: Set<String>,
    val sourceDirectories: Set<String>,
    val testDirectories: Set<String>,
    val resourceDirectories: Set<String>,
    val configFiles: Set<String>
)

/**
 * Detects project types based on files present in the repository.
 */
class ProjectTypeDetector {

    private val projectTypes = listOf(
        // Kotlin Multiplatform/Gradle
        ProjectType(
            name = "Kotlin Multiplatform",
            buildSystem = BuildSystem.GRADLE_KTS,
            primaryLanguage = ProgrammingLanguage.KOTLIN,
            supportedLanguages = setOf(ProgrammingLanguage.KOTLIN, ProgrammingLanguage.JAVA),
            buildFiles = setOf("build.gradle.kts", "settings.gradle.kts"),
            sourceDirectories = setOf("src/commonMain/kotlin", "src/jvmMain/kotlin", "src/androidMain/kotlin", "src/jsMain/kotlin", "src/nativeMain/kotlin"),
            testDirectories = setOf("src/commonTest/kotlin", "src/jvmTest/kotlin", "src/androidTest/kotlin"),
            resourceDirectories = setOf("src/commonMain/resources", "src/jvmMain/resources"),
            configFiles = setOf("gradle.properties", "local.properties")
        ),

        // Java/Gradle
        ProjectType(
            name = "Java Gradle",
            buildSystem = BuildSystem.GRADLE_GROOVY,
            primaryLanguage = ProgrammingLanguage.JAVA,
            supportedLanguages = setOf(ProgrammingLanguage.JAVA, ProgrammingLanguage.KOTLIN),
            buildFiles = setOf("build.gradle", "settings.gradle"),
            sourceDirectories = setOf("src/main/java", "src/main/kotlin"),
            testDirectories = setOf("src/test/java", "src/test/kotlin"),
            resourceDirectories = setOf("src/main/resources"),
            configFiles = setOf("gradle.properties")
        ),

        // Maven projects
        ProjectType(
            name = "Maven",
            buildSystem = BuildSystem.MAVEN,
            primaryLanguage = ProgrammingLanguage.JAVA,
            supportedLanguages = setOf(ProgrammingLanguage.JAVA, ProgrammingLanguage.KOTLIN, ProgrammingLanguage.SCALA, ProgrammingLanguage.GROOVY),
            buildFiles = setOf("pom.xml"),
            sourceDirectories = setOf("src/main/java", "src/main/kotlin", "src/main/scala", "src/main/groovy"),
            testDirectories = setOf("src/test/java", "src/test/kotlin", "src/test/scala"),
            resourceDirectories = setOf("src/main/resources"),
            configFiles = setOf("mvnw", "mvnw.cmd", ".mvn/wrapper/maven-wrapper.properties")
        ),

        // Node.js/npm
        ProjectType(
            name = "Node.js",
            buildSystem = BuildSystem.NPM,
            primaryLanguage = ProgrammingLanguage.JAVASCRIPT,
            supportedLanguages = setOf(ProgrammingLanguage.JAVASCRIPT, ProgrammingLanguage.TYPESCRIPT),
            buildFiles = setOf("package.json"),
            sourceDirectories = setOf("src", "lib", "app"),
            testDirectories = setOf("test", "__tests__", "spec"),
            resourceDirectories = setOf("public", "assets", "static"),
            configFiles = setOf("tsconfig.json", ".eslintrc.js", ".babelrc")
        ),

        // Python projects
        ProjectType(
            name = "Python",
            buildSystem = BuildSystem.PIP,
            primaryLanguage = ProgrammingLanguage.PYTHON,
            supportedLanguages = setOf(ProgrammingLanguage.PYTHON),
            buildFiles = setOf("requirements.txt", "setup.py", "pyproject.toml", "Pipfile"),
            sourceDirectories = setOf("src", "lib", "package"),
            testDirectories = setOf("tests", "test"),
            resourceDirectories = setOf("resources", "data"),
            configFiles = setOf("setup.cfg", "tox.ini", "pytest.ini")
        ),

        // Rust projects
        ProjectType(
            name = "Rust",
            buildSystem = BuildSystem.CARGO,
            primaryLanguage = ProgrammingLanguage.RUST,
            supportedLanguages = setOf(ProgrammingLanguage.RUST),
            buildFiles = setOf("Cargo.toml"),
            sourceDirectories = setOf("src"),
            testDirectories = setOf("tests"),
            resourceDirectories = setOf("resources", "assets"),
            configFiles = setOf("Cargo.lock", ".cargo/config")
        ),

        // Go projects
        ProjectType(
            name = "Go",
            buildSystem = BuildSystem.GO_MOD,
            primaryLanguage = ProgrammingLanguage.GO,
            supportedLanguages = setOf(ProgrammingLanguage.GO),
            buildFiles = setOf("go.mod"),
            sourceDirectories = setOf("cmd", "pkg", "internal"),
            testDirectories = setOf("_test.go"), // Go test files end with _test.go
            resourceDirectories = setOf("assets", "templates"),
            configFiles = setOf("go.sum", ".air.toml")
        ),

        // C/C++ projects
        ProjectType(
            name = "C/C++",
            buildSystem = BuildSystem.CMAKE,
            primaryLanguage = ProgrammingLanguage.CPP,
            supportedLanguages = setOf(ProgrammingLanguage.C, ProgrammingLanguage.CPP),
            buildFiles = setOf("CMakeLists.txt", "Makefile"),
            sourceDirectories = setOf("src", "include", "lib"),
            testDirectories = setOf("tests", "test"),
            resourceDirectories = setOf("resources", "assets"),
            configFiles = setOf("configure.ac", "Makefile.am")
        ),

        // C# projects
        ProjectType(
            name = "C#",
            buildSystem = BuildSystem.CSPROJ,
            primaryLanguage = ProgrammingLanguage.CSHARP,
            supportedLanguages = setOf(ProgrammingLanguage.CSHARP),
            buildFiles = setOf(".csproj", ".sln"),
            sourceDirectories = setOf("src", "lib"),
            testDirectories = setOf("tests", "test"),
            resourceDirectories = setOf("Resources", "Assets"),
            configFiles = setOf("packages.config", "Directory.Build.props")
        ),

        // Swift projects
        ProjectType(
            name = "Swift",
            buildSystem = BuildSystem.XCODEPROJ,
            primaryLanguage = ProgrammingLanguage.SWIFT,
            supportedLanguages = setOf(ProgrammingLanguage.SWIFT, ProgrammingLanguage.OBJECTIVE_C),
            buildFiles = setOf("Package.swift", ".xcodeproj"),
            sourceDirectories = setOf("Sources", "src"),
            testDirectories = setOf("Tests"),
            resourceDirectories = setOf("Resources", "Assets.xcassets"),
            configFiles = setOf("Info.plist", ".swiftlint.yml")
        )
    )

    /**
     * Detects the project types present in the given file list.
     */
    fun detectProjectTypes(files: List<String>): List<ProjectType> {
        val detectedTypes = mutableListOf<ProjectType>()

        for (projectType in projectTypes) {
            val hasBuildFiles = projectType.buildFiles.any { buildFile ->
                files.any { file -> file.endsWith(buildFile) || file.contains("/$buildFile") }
            }

            if (hasBuildFiles) {
                detectedTypes.add(projectType)
                DebugForgeLogger.debug("ProjectTypeDetector", "Detected project type: ${projectType.name}")
            }
        }

        // If no specific types detected, try to infer from source files
        if (detectedTypes.isEmpty()) {
            val inferredType = inferProjectTypeFromFiles(files)
            if (inferredType != null) {
                detectedTypes.add(inferredType)
                DebugForgeLogger.debug("ProjectTypeDetector", "Inferred project type: ${inferredType.name}")
            }
        }

        return detectedTypes
    }

    /**
     * Gets the primary project type from detected types.
     */
    fun getPrimaryProjectType(projectTypes: List<ProjectType>): ProjectType? {
        return projectTypes.firstOrNull()
    }

    /**
     * Infers project type from source files when no build files are found.
     */
    private fun inferProjectTypeFromFiles(files: List<String>): ProjectType? {
        val kotlinFiles = files.count { it.endsWith(".kt") || it.endsWith(".kts") }
        val javaFiles = files.count { it.endsWith(".java") }
        val jsFiles = files.count { it.endsWith(".js") || it.endsWith(".jsx") }
        val tsFiles = files.count { it.endsWith(".ts") || it.endsWith(".tsx") }
        val pythonFiles = files.count { it.endsWith(".py") }
        val rustFiles = files.count { it.endsWith(".rs") }
        val goFiles = files.count { it.endsWith(".go") }
        val cFiles = files.count { it.endsWith(".c") || it.endsWith(".h") }
        val cppFiles = files.count { it.endsWith(".cpp") || it.endsWith(".hpp") || it.endsWith(".cc") }
        val csharpFiles = files.count { it.endsWith(".cs") }
        val swiftFiles = files.count { it.endsWith(".swift") }

        return when {
            kotlinFiles > 0 -> projectTypes.find { it.primaryLanguage == ProgrammingLanguage.KOTLIN }
            javaFiles > 0 -> projectTypes.find { it.primaryLanguage == ProgrammingLanguage.JAVA }
            tsFiles > 0 -> projectTypes.find { it.primaryLanguage == ProgrammingLanguage.TYPESCRIPT }
            jsFiles > 0 -> projectTypes.find { it.primaryLanguage == ProgrammingLanguage.JAVASCRIPT }
            pythonFiles > 0 -> projectTypes.find { it.primaryLanguage == ProgrammingLanguage.PYTHON }
            rustFiles > 0 -> projectTypes.find { it.primaryLanguage == ProgrammingLanguage.RUST }
            goFiles > 0 -> projectTypes.find { it.primaryLanguage == ProgrammingLanguage.GO }
            cppFiles > 0 || cFiles > 0 -> projectTypes.find { it.primaryLanguage == ProgrammingLanguage.CPP }
            csharpFiles > 0 -> projectTypes.find { it.primaryLanguage == ProgrammingLanguage.CSHARP }
            swiftFiles > 0 -> projectTypes.find { it.primaryLanguage == ProgrammingLanguage.SWIFT }
            else -> null
        }
    }
}