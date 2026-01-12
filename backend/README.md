# DebugForge Backend

A production-grade Kotlin Multiplatform backend for the DebugForge debugging toolkit. This backend provides comprehensive analysis, diagnostics, and refactoring capabilities for Kotlin Multiplatform projects.

## Platform Support

| Platform | Status | Notes |
|----------|--------|-------|
| **JVM** | ✅ Full | Primary development platform |
| **JS (Browser)** | ✅ Full | Web-based analysis |
| **JS (Node.js)** | ✅ Full | Server-side JS |
| **iOS** | ✅ Compiles | Requires macOS for full testing |
| **macOS** | ✅ Compiles | Native desktop |
| **Linux** | ✅ Compiles | Native desktop |
| **Windows (mingw)** | ✅ Compiles | Native desktop |
| **Android** | ⚙️ Ready | Requires Android SDK (see notes) |
| **WasmJs** | ❌ Blocked | Needs SQLDelight 2.1+, Ktor 3.x |

### Android Setup

To enable Android support, you need:
1. Android SDK installed at `$LOCALAPPDATA/Android/Sdk`
2. Android platform tools (e.g., `platforms/android-34`)
3. Uncomment the Android target in `shared/build.gradle.kts`

### WasmJs Support

WasmJs target is currently disabled due to dependency incompatibilities:
- SQLDelight 2.0.x doesn't support WasmJs (needs 2.1+)
- Ktor 2.3.7 doesn't support WasmJs (needs 3.x)

When these dependencies are upgraded, enable the WasmJs target in `build.gradle.kts`.

## Features

- **Repository Analysis**: Load and analyze local KMP repositories
- **Project Indexing**: Parse and index Kotlin source files with incremental updates
- **Multi-Target Diagnostics**: Detect issues specific to KMP development
  - Missing `actual` implementations
  - Coroutine leaks and misuse
  - WASM thread safety violations
  - API misuse patterns
- **AI-Powered Refactoring**: Local-first suggestions with optional ML inference
- **Preview System**: Multi-platform preview orchestration
- **Shared Code Metrics**: Calculate and track code sharing percentages
- **Report Generation**: Export analysis results in Markdown, JSON, HTML, or Text formats

## Architecture

```
backend/
├── shared/                     # KMP shared module (85%+ of logic)
│   └── src/
│       ├── commonMain/         # Cross-platform code
│       │   └── kotlin/com/kmpforge/debugforge/
│       │       ├── state/      # StateFlow-based state management
│       │       ├── core/       # Repository loading, indexing, symbol extraction
│       │       ├── analysis/   # All analyzers (ExpectActual, Coroutine, WASM, API)
│       │       ├── diagnostics/# Diagnostic engine and emitter
│       │       ├── ai/         # Rule-based + ML inference engines
│       │       ├── persistence/# SQLDelight schema and DAOs
│       │       ├── preview/    # Multi-platform preview system
│       │       ├── config/     # Configuration models
│       │       ├── metrics/    # Operation metrics tracking
│       │       ├── export/     # Report generation
│       │       ├── watcher/    # File watching abstractions
│       │       └── utils/      # Logging, path utilities
│       └── jvmMain/            # JVM-specific implementations
│           └── kotlin/com/kmpforge/debugforge/
│               ├── core/       # JvmFileSystem, JvmGitOperations
│               ├── persistence/# SQLite driver factory
│               └── watcher/    # NIO-based file watcher
└── server/                     # Ktor HTTP/WebSocket server
    └── src/main/kotlin/
        └── Application.kt      # REST API + WebSocket endpoints
```

## Key Design Principles

1. **Offline-First**: All analysis runs locally without external services
2. **Privacy-Preserving**: Repository source code never leaves the user's machine
3. **StateFlow-Based**: Reactive state management, no callbacks
4. **Local AI**: Rule-based inference with optional ML fallback
5. **85% Shared Code**: Maximum code reuse across platforms

## Building

```bash
# Build the shared module
./gradlew :shared:build

# Build and run the server
./gradlew :server:run

# Create a fat JAR for deployment
./gradlew :server:fatJar
```

## Running the Server

```bash
# Start the HTTP server on port 8080
./gradlew :server:run

# Or run the fat JAR directly
java -jar server/build/libs/server-all.jar
```

## API Endpoints

### REST API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/api/state` | GET | Current state snapshot |
| `/api/repo/load` | POST | Load a local repository |
| `/api/repo/clone` | POST | Clone a remote repository |
| `/api/modules` | GET | List detected modules |
| `/api/diagnostics` | GET | Get all diagnostics |
| `/api/refactors` | GET | Get refactoring suggestions |
| `/api/refactors/{id}/apply` | POST | Apply a refactoring |
| `/api/previews` | GET | List active previews |
| `/api/previews/start` | POST | Start a preview session |
| `/api/previews/{id}/stop` | POST | Stop a preview session |

### WebSocket

Connect to `/ws` for real-time state updates:

```kotlin
// Events sent:
// - state_update: Full state snapshot
// - diagnostic: Individual diagnostic events
// - preview_update: Preview status changes
// - error: Error events
```

## Example Usage

### Loading a Repository

```bash
curl -X POST http://localhost:8080/api/repo/load \
  -H "Content-Type: application/json" \
  -d '{"path": "/path/to/kmp-project"}'
```

### Getting Diagnostics

```bash
curl http://localhost:8080/api/diagnostics
```

### Applying a Refactoring

```bash
curl -X POST http://localhost:8080/api/refactors/suggestion-123/apply
```

## Configuration

Create a `debugforge.json` in the repository root:

```json
{
  "databasePath": null,
  "verboseLogging": false,
  "maxParallelFiles": 10,
  "enableMlInference": false,
  "analyzers": {
    "enableExpectActualAnalyzer": true,
    "enableCoroutineLeakDetector": true,
    "enableWasmThreadSafetyAnalyzer": true,
    "enableApiMisuseAnalyzer": true
  },
  "preview": {
    "desktopPort": 9100,
    "browserPort": 9200,
    "enableHotReload": true
  }
}
```

## Dependencies

- **Kotlin**: 1.9.22
- **kotlinx.coroutines**: 1.7.3
- **kotlinx.serialization**: 1.6.2
- **kotlinx.datetime**: 0.5.0
- **SQLDelight**: 2.0.1
- **Ktor**: 2.3.7 (server module only)
- **JGit**: 6.7.0 (JVM only)

## Analyzers

### ExpectActualAnalyzer
Detects missing `actual` declarations, signature mismatches, and visibility mismatches between expect/actual pairs.

### CoroutineLeakDetector
Identifies GlobalScope usage, missing dispatchers, blocking calls on Main dispatcher, and Job leak patterns.

### WasmThreadSafetyAnalyzer
Finds illegal Dispatchers.IO usage, Thread API usage, and unsafe shared mutable state in WASM targets.

### ApiMisuseAnalyzer
Detects nullable assertion (!!) abuse, resource leak patterns, and platform API leaks across module boundaries.

## License

MIT
