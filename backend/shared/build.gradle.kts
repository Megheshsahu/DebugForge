plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("app.cash.sqldelight")
    id("com.google.devtools.ksp")
}

kotlin {
    jvmToolchain(17)
    
    sourceSets {
        val main by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                implementation("co.touchlab:stately-common:2.0.5")
                implementation("co.touchlab:stately-concurrency:2.0.5")
                implementation("io.ktor:ktor-client-core:2.3.7")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
                implementation("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r")
                implementation("app.cash.sqldelight:sqlite-driver:2.0.1")
                implementation("com.google.devtools.ksp:symbol-processing-api:1.9.22-1.0.17")
                implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.23")
                implementation("io.ktor:ktor-client-cio:2.3.7")
            }
        }
        
        val test by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
    }
}

sqldelight {
    databases {
        create("DebugForgeDatabase") {
            packageName.set("com.kmpforge.debugforge.db")
            generateAsync.set(true)
        }
    }
}
