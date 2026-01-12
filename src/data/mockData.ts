import type { Module, Diagnostic, AIRefactorSuggestion, SourceFile, ScanResult } from "@/types/debug";

// Mock modules data
export const mockModules: Module[] = [
  {
    id: "shared",
    name: "shared",
    path: ":shared",
    sharedCodePercent: 100,
    expectCount: 12,
    actualCount: 36,
    coverage: 75,
    children: [
      {
        id: "shared-domain",
        name: "domain",
        path: ":shared:domain",
        sharedCodePercent: 100,
        expectCount: 0,
        actualCount: 0,
        coverage: 100,
      },
      {
        id: "shared-data",
        name: "data",
        path: ":shared:data",
        sharedCodePercent: 85,
        expectCount: 5,
        actualCount: 15,
        coverage: 67,
        hasWarnings: true,
      },
      {
        id: "shared-ui",
        name: "ui",
        path: ":shared:ui",
        sharedCodePercent: 92,
        expectCount: 7,
        actualCount: 21,
        coverage: 85,
        hasErrors: true,
      },
    ],
  },
  {
    id: "android",
    name: "androidApp",
    path: ":androidApp",
    sharedCodePercent: 0,
    expectCount: 0,
    actualCount: 12,
    coverage: 100,
  },
  {
    id: "ios",
    name: "iosApp",
    path: ":iosApp",
    sharedCodePercent: 0,
    expectCount: 0,
    actualCount: 8,
    coverage: 100,
    hasWarnings: true,
  },
  {
    id: "desktop",
    name: "desktopApp",
    path: ":desktopApp",
    sharedCodePercent: 0,
    expectCount: 0,
    actualCount: 4,
    coverage: 100,
  },
  {
    id: "web",
    name: "webApp",
    path: ":webApp",
    sharedCodePercent: 0,
    expectCount: 0,
    actualCount: 6,
    coverage: 83,
    hasErrors: true,
  },
];

export const mockDiagnostics: Diagnostic[] = [
  {
    id: "diag-1",
    severity: "error",
    category: "coroutine",
    title: "Coroutine leak detected",
    description: "GlobalScope.launch in ViewModel creates unbounded coroutine lifecycle. May cause memory leaks and zombie coroutines.",
    location: {
      file: "shared/src/commonMain/kotlin/data/repository/UserRepository.kt",
      line: 47,
      column: 12,
    },
    platforms: ["android", "ios", "desktop"],
  },
  {
    id: "diag-2",
    severity: "error",
    category: "wasm",
    title: "Wasm threading violation",
    description: "Direct Worker thread access not supported in Kotlin/Wasm. Use withContext(Dispatchers.Default) instead.",
    location: {
      file: "shared/src/commonMain/kotlin/util/CryptoUtils.kt",
      line: 89,
    },
    platforms: ["web"],
  },
  {
    id: "diag-3",
    severity: "warning",
    category: "platform-api",
    title: "Missing actual declaration",
    description: "expect fun getDeviceId() has no actual implementation for iOS. Will cause runtime crash.",
    location: {
      file: "shared/src/commonMain/kotlin/platform/DeviceInfo.kt",
      line: 23,
    },
    platforms: ["ios"],
  },
  {
    id: "diag-4",
    severity: "warning",
    category: "memory",
    title: "Potential memory pressure",
    description: "Large bitmap retained in remember block without cleanup. Add DisposableEffect for proper resource management.",
    location: {
      file: "shared/src/commonMain/kotlin/ui/components/ImageViewer.kt",
      line: 156,
      column: 8,
    },
    platforms: ["android", "ios"],
  },
  {
    id: "diag-5",
    severity: "info",
    category: "threading",
    title: "Dispatcher optimization",
    description: "Consider using Dispatchers.IO for file operations to avoid blocking main thread.",
    location: {
      file: "shared/src/commonMain/kotlin/data/local/FileStorage.kt",
      line: 34,
    },
    platforms: ["android", "ios", "desktop"],
  },
];

export const mockSuggestions: AIRefactorSuggestion[] = [
  {
    id: "sug-1",
    title: "Replace GlobalScope with viewModelScope",
    description: "Binds coroutine lifecycle to ViewModel. Automatically cancelled when ViewModel is cleared.",
    confidence: 0.95,
    before: `class UserRepository {
    fun fetchUser(id: String) {
        GlobalScope.launch {
            val user = api.getUser(id)
            _userFlow.emit(user)
        }
    }
}`,
    after: `class UserRepository(
    private val scope: CoroutineScope
) {
    fun fetchUser(id: String) {
        scope.launch {
            val user = api.getUser(id)
            _userFlow.emit(user)
        }
    }
}`,
    location: {
      file: "shared/src/commonMain/kotlin/data/repository/UserRepository.kt",
      startLine: 45,
      endLine: 52,
    },
    status: "pending",
  },
  {
    id: "sug-2",
    title: "Add expect/actual for iOS DeviceInfo",
    description: "Creates platform-specific implementation using UIDevice API for iOS.",
    confidence: 0.88,
    before: `expect fun getDeviceId(): String`,
    after: `// commonMain
expect fun getDeviceId(): String

// iosMain
actual fun getDeviceId(): String {
    return UIDevice.currentDevice.identifierForVendor
        ?.UUIDString ?: "unknown"
}`,
    location: {
      file: "shared/src/commonMain/kotlin/platform/DeviceInfo.kt",
      startLine: 23,
      endLine: 23,
    },
    status: "pending",
  },
  {
    id: "sug-3",
    title: "Add DisposableEffect for bitmap cleanup",
    description: "Ensures proper resource cleanup when composable leaves composition.",
    confidence: 0.92,
    before: `@Composable
fun ImageViewer(url: String) {
    val bitmap = remember { loadBitmap(url) }
    Image(bitmap = bitmap)
}`,
    after: `@Composable
fun ImageViewer(url: String) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    
    DisposableEffect(url) {
        val loaded = loadBitmap(url)
        bitmap = loaded
        onDispose { 
            loaded.recycle()
        }
    }
    
    bitmap?.let { Image(bitmap = it) }
}`,
    location: {
      file: "shared/src/commonMain/kotlin/ui/components/ImageViewer.kt",
      startLine: 154,
      endLine: 158,
    },
    status: "pending",
  },
];

export const mockSourceFile: SourceFile = {
  path: "shared/src/commonMain/kotlin/data/repository/UserRepository.kt",
  language: "kotlin",
  content: `package com.example.app.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class UserRepository(
    private val api: UserApi,
    private val cache: UserCache
) {
    private val _userFlow = MutableStateFlow<User?>(null)
    val userFlow: StateFlow<User?> = _userFlow.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    suspend fun getUser(id: String): Result<User> {
        return try {
            _loading.emit(true)
            val cached = cache.get(id)
            if (cached != null) {
                _userFlow.emit(cached)
                return Result.success(cached)
            }
            
            val user = api.fetchUser(id)
            cache.put(id, user)
            _userFlow.emit(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _loading.emit(false)
        }
    }

    fun observeUser(id: String): Flow<User?> {
        return flow {
            emit(cache.get(id))
            emitAll(api.observeUser(id))
        }.onEach { user ->
            user?.let { cache.put(id, it) }
        }
    }

    fun fetchUserInBackground(id: String) {
        GlobalScope.launch {
            val user = api.fetchUser(id)
            _userFlow.emit(user)
        }
    }

    suspend fun updateUser(user: User): Result<User> {
        return try {
            val updated = api.updateUser(user)
            cache.put(user.id, updated)
            _userFlow.emit(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}`,
  annotations: [
    {
      line: 47,
      type: "error",
      message: "GlobalScope creates unbounded coroutine lifecycle",
      suggestion: "Replace with injected CoroutineScope",
    },
    {
      line: 48,
      type: "ai-suggestion",
      message: "AI: Inject scope parameter for proper lifecycle management",
    },
  ],
};

export const mockScanResult: ScanResult = {
  modules: mockModules,
  diagnostics: mockDiagnostics,
  suggestions: mockSuggestions,
  stats: {
    totalFiles: 127,
    sharedFiles: 89,
    platformSpecificFiles: 38,
    errorsCount: 2,
    warningsCount: 2,
    suggestionsCount: 3,
  },
};
