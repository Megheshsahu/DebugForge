# 🔧 DebugForge

**AI-Powered Kotlin Multiplatform Debugger & Analyzer**

> A fully-functional developer tool that analyzes KMP projects, detects multiplatform-specific issues, and provides intelligent refactoring suggestions - built entirely with Kotlin Multiplatform and Compose Multiplatform.

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose%20Multiplatform-1.5.12-green.svg)](https://github.com/JetBrains/compose-multiplatform)
[![Shared Code](https://img.shields.io/badge/Shared%20Code-87.47%25-brightgreen.svg)](#shared-code-percentage)

---

## 🎯 Project Overview

DebugForge is a **developer productivity tool** that helps Kotlin Multiplatform developers find and fix issues in their codebase. It demonstrates the power of KMP by using the same technology stack to build the tool itself.

### Key Features

| Feature | Description |
|---------|-------------|
| **🔍 Static Analysis** | Detects KMP-specific issues like missing `actual` implementations, coroutine safety problems, and WASM compatibility issues |
| **🤖 AI Suggestions** | Provides intelligent refactoring suggestions based on rule-based and ML inference engines |
| **📊 Code Metrics** | Calculates shared code percentage across all source sets |
| **🖥️ Desktop UI** | Native Compose Desktop application for macOS, Windows, and Linux |
| **🌐 REST API** | Backend server with WebSocket support for real-time updates |

---

## 📐 Architecture

```
kmp-forge-main/
└── backend/
    ├── shared/                     # 🟢 KMP shared module (87% of code)
    │   ├── commonMain/             # Cross-platform logic
    │   │   ├── analysis/           # Analyzers (ExpectActual, Coroutine, WASM, API)
    │   │   ├── diagnostics/        # Issue detection engine
    │   │   ├── ai/                 # Rule-based + ML inference
    │   │   ├── persistence/        # SQLDelight database
    │   │   └── core/               # Repository loading, indexing
    │   └── jvmMain/                # JVM file system implementations
    │
    ├── composeApp/                 # 🟢 Compose Multiplatform UI
    │   ├── commonMain/             # Shared UI components
    │   └── desktopMain/            # Desktop window entry point
    │
    └── server/                     # Ktor HTTP server
        └── Application.kt          # REST API endpoints
```

---

## 📊 Shared Code Percentage

| Module | Lines | Type |
|--------|-------|------|
| `shared/commonMain` | 7,685 | ✅ Shared |
| `composeApp/commonMain` | 760 | ✅ Shared |
| `shared/jvmMain` | 769 | Platform |
| `server` | 425 | Platform |
| `composeApp/desktopMain` | 16 | Platform |

### **Total: 87.47% Shared Code** ✅

---

## 🚀 Getting Started

### Prerequisites

- **JDK 17+** (Microsoft OpenJDK recommended)
- **Gradle 8.5+**

### Run the Backend Server

```bash
cd backend
./gradlew :server:run
```

Server starts at `http://localhost:8765`

### Run the Compose Desktop App

```bash
cd backend
./gradlew :composeApp:run
```

### API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/repo/load` | POST | Load a KMP repository for analysis |
| `/api/repo/{id}/analyze` | POST | Run all analyzers |
| `/api/repo/{id}/diagnostics` | GET | Get detected issues |
| `/api/repo/{id}/suggestions` | GET | Get refactoring suggestions |
| `/api/repo/{id}/metrics` | GET | Get shared code metrics |

---

## 🔬 Analyzers

### 1. ExpectActual Analyzer
Detects missing `actual` declarations for `expect` declarations across all platforms.

### 2. Coroutine Safety Analyzer
Finds coroutine-related issues:
- Dispatchers.IO usage in non-JVM contexts
- Missing coroutine scoping
- Potential memory leaks

### 3. WASM Compatibility Analyzer
Identifies code that won't work in WASM:
- Thread API usage
- Reflection calls
- Blocking operations

### 4. API Misuse Analyzer
Catches common KMP API mistakes:
- Platform-specific APIs in commonMain
- Incorrect expect/actual patterns

---

## 🛠️ Technology Stack

| Component | Technology |
|-----------|------------|
| **Language** | Kotlin 1.9.22 |
| **UI Framework** | Compose Multiplatform 1.5.12 |
| **Backend** | Ktor 2.3.7 |
| **Database** | SQLDelight 2.0.1 |
| **Serialization** | Kotlinx Serialization 1.6.2 |
| **Concurrency** | Kotlinx Coroutines 1.7.3 |

---

## 🎮 Demo

1. Start the backend: `./gradlew :server:run`
2. Start the UI: `./gradlew :composeApp:run`
3. Enter a KMP project path (e.g., `D:/Projects/my-kmp-app`)
4. Click "Scan Repository"
5. View diagnostics and AI suggestions

---

## 📝 Contest Criteria

### Creativity (40%)
- **Novel approach**: Developer tool built WITH KMP to analyze KMP projects
- **Real-world utility**: Solves actual pain points in KMP development
- **AI integration**: Rule-based + extensible ML inference engine

### KMP Usage (40%)
- **87.47% shared code** across all modules
- **Compose Multiplatform UI** with Desktop support
- **SQLDelight** for cross-platform persistence
- **Ktor client/server** for networking

### Code Quality (20%)
- **Clean architecture**: State management, analyzers, persistence layers
- **Type-safe**: SQLDelight queries, sealed classes for state
- **Testable**: Separated concerns, dependency injection

---

## 📜 License

MIT License

---

**Built with ❤️ using Kotlin Multiplatform**
