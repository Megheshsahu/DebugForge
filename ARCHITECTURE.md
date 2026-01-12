# DebugForge Architecture

## 🏗️ Complete Cross-Platform Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     User Interfaces                          │
├──────────────┬──────────────┬──────────────┬────────────────┤
│   Desktop    │   Android    │   Web/Wasm   │   iOS (Future) │
│   (JVM)      │  (Android)   │  (Browser)   │   (Native)     │
└──────┬───────┴──────┬───────┴──────┬───────┴────────┬───────┘
       │              │              │                │
       │              │              │                │
       ▼              ▼              ▼                ▼
┌─────────────────────────────────────────────────────────────┐
│              Compose Multiplatform UI (commonMain)          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  App.kt - Main UI                                     │  │
│  │  DebugForgeViewModel - State Management               │  │
│  │  - Project loading & analysis                         │  │
│  │  - Diagnostics & suggestions display                  │  │
│  │  - GitHub sync coordination                           │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│            Shared Business Logic (commonMain)                │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────┐  │
│  │  AI Service    │  │  GitHub Sync   │  │  Analysis    │  │
│  │                │  │                │  │  Engine      │  │
│  │ - GroqAI       │  │ - File R/W     │  │              │  │
│  │ - Code analysis│  │ - Branch mgmt  │  │ - Diagnostics│  │
│  │ - Fix gen      │  │ - PR creation  │  │ - Metrics    │  │
│  └────────────────┘  └────────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│        Platform Abstraction (expect/actual)                  │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  PlatformFileSystem (expect)                          │   │
│  │  - exists(), readFile(), writeFile()                  │   │
│  │  - listFiles(), isDirectory()                         │   │
│  │  - pickProjectFolder()                                │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                               │
│  ┌────────────┬────────────┬────────────┬──────────────┐   │
│  │  Desktop   │  Android   │   Wasm     │   iOS        │   │
│  │  (actual)  │  (actual)  │  (actual)  │  (actual)    │   │
│  ├────────────┼────────────┼────────────┼──────────────┤   │
│  │ Java File  │ Storage    │ File API   │ FileManager  │   │
│  │ I/O        │ Access     │ (Browser)  │ (Foundation) │   │
│  │            │ Framework  │            │              │   │
│  │ JFileChsr  │ DocumentFl │ IndexedDB  │ NSOpenPanel  │   │
│  └────────────┴────────────┴────────────┴──────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              External Services & APIs                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │  Groq AI API │  │  GitHub API  │  │  Backend     │     │
│  │              │  │              │  │  Server      │     │
│  │ Analysis     │  │ Repo access  │  │  (Optional)  │     │
│  │ Suggestions  │  │ PR creation  │  │              │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔄 Data Flow Examples

### Example 1: Desktop - Local Project Analysis with Apply

```
[User] → Browse folder → [Desktop FileSystem]
                              ↓
                         Read project files
                              ↓
                    [Analysis Engine] ← [AI Service]
                              ↓
                      Generate suggestions
                              ↓
                         [ViewModel]
                              ↓
                          [UI Display]
                              ↓
[User] → Click Apply → [DesktopApplyService]
                              ↓
                         Write to file
                              ↓
                     Run Gradle build
                              ↓
                    ┌─── Success? ───┐
                    │                 │
                   YES               NO
                    │                 │
                    ▼                 ▼
            Keep changes      Rollback file
                    │                 │
                    └─────────┬───────┘
                              ↓
                    Update UI status
```

### Example 2: Android - Analysis with GitHub Sync

```
[User] → Browse folder → [Android SAF]
                              ↓
                    Pick project folder
                              ↓
                  Grant URI permission
                              ↓
                 [Android FileSystem]
                              ↓
                  Read files via ContentResolver
                              ↓
                    [Analysis Engine] ← [AI Service]
                              ↓
                      Generate suggestions
                              ↓
                         [ViewModel]
                              ↓
                          [UI Display]
                              ↓
[User] → Sync to GitHub → [GitHub Dialog]
                              ↓
                    Enter owner/repo/path
                              ↓
                       [SyncManager]
                              ↓
              ┌─── GitHub API calls ───┐
              │                         │
        Get file SHA           Create branch
              │                         │
              └──────────┬──────────────┘
                         ↓
                   Update file
                         ↓
                 Create Pull Request
                         ↓
                   Return PR URL
                         ↓
              [UI shows success banner]
```

### Example 3: Cross-Platform GitHub Workflow

```
┌───────────────┐        ┌───────────────┐        ┌──────────────┐
│   Android     │        │    GitHub     │        │   Desktop    │
│   Phone       │        │   (Cloud)     │        │   PC         │
└───────┬───────┘        └───────┬───────┘        └──────┬───────┘
        │                        │                        │
        │  1. Analyze code       │                        │
        ├───────────────────────►│                        │
        │                        │                        │
        │  2. Create branch      │                        │
        ├───────────────────────►│                        │
        │     "debugforge-fix-x" │                        │
        │                        │                        │
        │  3. Commit change      │                        │
        ├───────────────────────►│                        │
        │                        │                        │
        │  4. Create PR #42      │                        │
        ├───────────────────────►│                        │
        │                        │                        │
        │  5. Show PR URL        │                        │
        │◄───────────────────────┤                        │
        │                        │                        │
        │                        │  6. Notification       │
        │                        ├───────────────────────►│
        │                        │     "New PR #42"       │
        │                        │                        │
        │                        │  7. Review PR          │
        │                        │◄───────────────────────┤
        │                        │                        │
        │                        │  8. Merge PR           │
        │                        │◄───────────────────────┤
        │                        │                        │
        │                        │  9. git pull           │
        │                        ├───────────────────────►│
        │                        │    (changes applied)   │
        │                        │                        │
```

---

## 🎯 Key Architectural Decisions

### 1. **Expect/Actual Pattern**
```kotlin
// commonMain - Shared interface
expect class PlatformFileSystem() {
    fun readFile(path: String): String
    fun writeFile(path: String, content: String)
}

// desktopMain - JVM implementation
actual class PlatformFileSystem {
    actual fun readFile(path: String) = File(path).readText()
    actual fun writeFile(path: String, content: String) = File(path).writeText(content)
}

// androidMain - Android implementation
actual class PlatformFileSystem {
    actual fun readFile(path: String) = /* ContentResolver */ ...
    actual fun writeFile(path: String, content: String) = /* ContentResolver */ ...
}
```

**Benefits:**
- Single shared codebase (90%+ code reuse)
- Platform-specific optimizations where needed
- Type-safe abstractions
- Easy to test

### 2. **Analysis Engine in commonMain**
```
backend/shared/src/commonMain/
├── analysis/           # Platform-agnostic analyzers
├── diagnostics/        # Issue detection
├── metrics/            # Code metrics
└── core/              # Project parsing
```

**Benefits:**
- Same analysis logic on all platforms
- Consistent results everywhere
- Easy to maintain
- Can run offline

### 3. **AI as Enhancement, Not Dependency**
```kotlin
if (AIConfig.ENABLE_AI && apiKey.isNotEmpty()) {
    // Use AI for intelligent suggestions
    aiService.analyzeCode(...)
} else {
    // Fallback to pattern-based analysis
    patternEngine.analyze(...)
}
```

**Benefits:**
- Works without AI (pattern-based fallback)
- AI improves accuracy when available
- Cost-controlled (user provides key)
- Privacy-conscious (can work offline)

### 4. **GitHub for Cross-Platform Sync**
```
Android ──┐
          ├──► GitHub (Source of Truth) ◄──┬── Desktop
Web ──────┘                                  └── iOS
```

**Benefits:**
- No complex P2P networking
- Version control built-in
- Code review workflow
- Industry-standard tool
- Free for public repos

### 5. **Verification Only Where Possible**
```
Desktop: Apply → Gradle → Verify → Keep/Rollback
Android: Apply → GitHub PR → Desktop verifies
Web:     Apply → GitHub PR → Desktop verifies
```

**Benefits:**
- Full verification on Desktop (where Gradle exists)
- Android/Web delegate to Desktop via GitHub
- No need to run Gradle on mobile
- Safe workflow for all platforms

---

## 📊 Component Responsibilities

### UI Layer (Compose Multiplatform)
**Responsibility:** Display data, handle user input
- App.kt - Main UI composition
- Screens (Loading, Error, Workspace)
- Cards (Module, Diagnostic, Suggestion)
- Dialogs (Diff, GitHub sync)

**Doesn't:**
- Business logic
- File I/O
- Network calls

### ViewModel Layer
**Responsibility:** State management, coordinate services
- Load/reload projects
- Trigger analysis
- Apply fixes
- GitHub sync coordination
- Error handling

**Doesn't:**
- Direct file access
- UI composition
- Platform-specific code

### Service Layer (commonMain)
**Responsibility:** Business logic, API calls
- AIService - Code analysis via Groq
- GitHubService - GitHub REST API calls
- SyncManager - Orchestrate sync workflow
- AnalysisEngine - Pattern-based analysis

**Doesn't:**
- File I/O (uses PlatformFileSystem)
- UI state
- Platform specifics

### Platform Layer (expect/actual)
**Responsibility:** Platform-specific implementations
- File system access
- Folder pickers
- Gradle execution (Desktop only)
- Build verification (Desktop only)

**Doesn't:**
- Business logic
- UI
- Network calls

---

## 🚦 State Management

```kotlin
sealed class UiState {
    object Idle                    // Initial state
    data class Loading(message)    // During operations
    data class Ready(data)         // Showing results
    data class Error(message)      // Error state
}
```

**Flow:**
```
Idle → Loading → Ready → [User action] → Loading → Ready
  ↑                ↓
  └─── Error ◄────┘
```

**State Flow:**
```kotlin
class DebugForgeViewModel {
    private val _uiState = MutableStateFlow<UiState>(Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // UI observes this flow and recomposes on changes
}
```

---

## 🔒 Security Considerations

### API Keys
- ❌ Not committed to Git
- ✅ User provides their own
- ✅ Stored in config objects
- 🔄 TODO: Use secure storage (KeyChain/Keystore)

### File Access
- Desktop: Full access (trusted environment)
- Android: SAF permissions (user grants)
- Web: No local access (upload only)

### GitHub Token
- Requires `repo` scope only
- Used for PR creation (transparent)
- Token never sent to our backend

---

## 📈 Scalability

### Adding New Platforms
1. Add target in build.gradle.kts
2. Create `{platform}Main` source set
3. Implement `actual` for PlatformFileSystem
4. Test & ship

### Adding New Features
1. Add to `commonMain` if platform-agnostic
2. Add `expect`/`actual` if platform-specific
3. Update UI in shared Compose code
4. Works on all platforms automatically

### Adding New AI Providers
1. Implement AIService interface
2. Add config in AIConfig.kt
3. Switch provider at runtime
4. No UI changes needed

---

## 🎉 Result

A **truly cross-platform** code analysis tool that:
- Runs natively on Desktop, Android, Web
- Shares 90%+ of codebase
- Adapts to platform capabilities
- Provides safe apply with verification
- Enables collaboration via GitHub
- Uses AI for intelligent suggestions
- Maintains professional architecture

**Perfect for:**
- Contest submission ✅
- Real-world use ✅
- Portfolio project ✅
- Learning KMP best practices ✅
