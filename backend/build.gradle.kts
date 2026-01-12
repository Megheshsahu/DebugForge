plugins {
    kotlin("multiplatform") version "1.9.23" apply false
    kotlin("plugin.serialization") version "1.9.23" apply false
    id("app.cash.sqldelight") version "2.0.1" apply false
    id("com.google.devtools.ksp") version "1.9.23-1.0.20" apply false
    id("com.android.application") version "8.2.0" apply false
}

allprojects {
    group = "com.kmpforge.debugforge"
    version = "1.0.0"
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs += listOf(
                "-Xcontext-receivers",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
            )
        }
    }
}
