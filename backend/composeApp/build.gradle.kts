import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform") version "1.9.23"
    id("org.jetbrains.compose") version "1.5.12"
    kotlin("plugin.serialization")
    id("com.android.application")
    id("app.cash.sqldelight")
}

compose {
    kotlinCompilerPlugin.set("1.5.12")
    kotlinCompilerPluginArgs.add("suppressKotlinVersionCompatibilityCheck=true")
}

kotlin {
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }
    
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }
    
    // wasmJs {
    //     browser()
    //     binaries.executable()
    // }
    
    sourceSets {
                val commonTest by getting {
                    dependencies {
                        implementation(kotlin("test"))
                    }
                }
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                
                // Ktor for API calls
                implementation("io.ktor:ktor-client-core:2.3.7")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
                
                // Kotlinx
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                
                // Git operations
                implementation("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r")
                
                // Database
                implementation("app.cash.sqldelight:sqlite-driver:2.0.1")
                
                // implementation(project(":shared"))
            }
        }
        
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("io.ktor:ktor-client-cio:2.3.7")
                implementation(project(":shared"))
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:2.3.7")
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.documentfile:documentfile:1.0.1")
                implementation("androidx.security:security-crypto:1.1.0-alpha06")
                implementation(compose.uiTooling)
                implementation("androidx.compose.material3:material3:1.1.2")
            }
        }
        
        // val wasmJsMain by getting {
        //     dependencies {
        //         implementation("io.ktor:ktor-client-js:2.3.7")
        //     }
        // }
    }
}

android {
    namespace = "com.kmpforge.debugforge"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kmpforge.debugforge"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.12"
    }

    packaging {
        resources {
            excludes += "kotlin/**"
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/*.kotlin_module"
            // Exclude shared module classes to avoid duplicates
            excludes += "com/kmpforge/debugforge/**"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "com.kmpforge.debugforge.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "DebugForge"
            packageVersion = "1.0.0"
            windows {
                menuGroup = "DebugForge"
                upgradeUuid = "A8D3E1C9-5B2F-4A7D-9E6C-1F3B8D4A2C5E"
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
