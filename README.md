# DebugForge

![Project Logo](assets/logo.png)

> _Add a screenshot of the app below (replace with your image path)_
>
> ![App Screenshot](assets/screenshot.png)

DebugForge is a Kotlin Multiplatform application for analyzing and fixing issues in Kotlin Multiplatform projects. It provides AI-powered code analysis, automated refactoring suggestions, and seamless integration with GitHub for collaborative development.

## System Architecture & Data Flow

![System Architecture & Data Flow](assets/data-flow-diagram.png)

*This diagram shows the complete workflow, data flow, and component interactions for DebugForge across all platforms.*

## Features

- **Multiplatform Support**: Runs on Desktop (Windows), Android, and Web (WASM)
- **AI-Powered Analysis**: Uses Grok AI for intelligent code suggestions
- **GitHub Integration**: Clone repositories, create branches, and submit pull requests
- **Undo/Redo System**: Track and revert applied fixes
- **Secure Storage**: Encrypted storage for API keys and tokens
- **Real-time Diagnostics**: Comprehensive analysis of KMP project structure
- **Embedded Server**: Built-in Ktor server for backend operations
- **File Picker Support**: Native file selection dialogs for each platform
- **Complete API Surface**: Full REST API for diagnostics, modules, suggestions, and metrics

## Prerequisites

### System Requirements
- **Operating System**: Windows 10/11 (for Desktop), Android 8.0+ (for Android)
- **Java Development Kit (JDK)**: JDK 17 or later
- **Android SDK**: Required for Android builds
  - Download Android Studio from https://developer.android.com/studio
  - Or install command-line tools only
- **Node.js**: 18+ and npm (for frontend development)
- **Git**: For cloning repositories

### API Keys Required
- **Groq API Key**: Obtain from [Groq Console](https://console.groq.com/)
- **GitHub Personal Access Token**: Create at [GitHub Settings > Developer settings > Personal access tokens](https://github.com/settings/tokens)
  - Required scopes: `repo`, `workflow`, `read:org`

## Installation and Setup

### 1. Clone the Repository

```bash
git clone https://github.com/your-username/debugforge.git
cd debugforge
```

### 2. Set up Android SDK

Create `backend/local.properties` file with your Android SDK path:

```bash
# Windows
echo "sdk.dir=C:\\Users\\YourName\\AppData\\Local\\Android\\Sdk" > backend/local.properties

# macOS
echo "sdk.dir=/Users/YourName/Library/Android/sdk" > backend/local.properties

# Linux
echo "sdk.dir=/home/YourName/Android/Sdk" > backend/local.properties
```

### 3. Configure API Keys (Optional but Recommended)

Update the configuration files with your API keys:

```kotlin
// backend/composeApp/src/commonMain/kotlin/com/kmpforge/debugforge/config/AIConfig.kt
var GROQ_API_KEY: String = "your_groq_api_key_here"

// backend/composeApp/src/commonMain/kotlin/com/kmpforge/debugforge/config/GitHubConfig.kt
var GITHUB_TOKEN: String = "your_github_token_here"
```

### 4. Build the Project

The project uses Gradle with Kotlin DSL. Ensure you have JDK 17+ installed.

#### On Windows (PowerShell):

```powershell
# Navigate to backend directory
cd backend

# Build all targets
.\gradlew.bat build

# Or build specific targets
.\gradlew.bat :composeApp:assembleDebug  # Android debug APK
.\gradlew.bat :composeApp:createDistributable  # Desktop distributable
.\gradlew.bat :server:build  # Build the backend server
```


### 5. Desktop Application Setup

#### Build the Desktop Distributable

```bash
cd backend
./gradlew composeApp:createDistributable
```

This creates a native Windows executable at:
`backend/composeApp/build/compose/binaries/main/app/DebugForge/DebugForge.exe`

#### Run the Desktop Application

For development, run directly from Gradle:

```bash
cd backend
./gradlew composeApp:run
```

This will compile and launch the DebugForge desktop application.

**Alternative: Run the built executable**

1. After building the distributable (see above), navigate to the build directory:
   ```bash
   cd backend/composeApp/build/compose/binaries/main/app/DebugForge
   ```

2. Launch the application:
   ```bash
   ./DebugForge.exe
   ```

3. The application window will open. Click "Settings" in the top-right corner.

4. Enter your Groq API key and GitHub token in the respective fields.

5. Click "Save Configuration" to store the credentials securely.

6. Click "Start Server" to launch the embedded backend server on port 8081.

### 7. Backend Server (Optional)

If you want to run the backend server separately:

```bash
cd backend
.\gradlew.bat :server:run
```

The server will start on `http://localhost:8081`.

### 5. Android Application Setup

#### Prerequisites for Android
- Android Studio Arctic Fox or later
- Android SDK with API level 24+
- Connected Android device or emulator

#### Build and Install APK

1. Ensure Android SDK is properly configured (see step 2 in Installation).

2. Build the debug APK:
   ```bash
   cd backend
   ./gradlew composeApp:assembleDebug
   ```

3. Install on connected device:
   ```bash
   ./gradlew composeApp:installDebug
   ```

4. The APK will be available at: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

#### Run on Android Device

1. Launch the DebugForge app on your Android device.

2. Grant necessary permissions if prompted.

3. Tap "Settings" to configure API keys.

4. Enter your Groq API key and GitHub token.

5. Tap "Save Configuration".

6. Tap "Start Server" to launch the backend.

### 6. Web Application (WASM) Setup

#### Run the Web Application

For development, run the web application directly:

```bash
cd backend
./gradlew composeApp:wasmJsBrowserRun
```

This will start a development server and open the application in your default browser at `http://localhost:8080`.

#### Build for Production

```bash
cd backend
./gradlew composeApp:wasmJsBrowserDistribution
```

#### Serve the Web Application

The built files will be in `backend/composeApp/build/dist/wasmJs/productionExecutable/`

Use any static web server to serve these files:

```bash
cd backend/composeApp/build/dist/wasmJs/productionExecutable
python -m http.server 8080
```

Open `http://localhost:8080` in a modern browser.

### 7. Frontend Development (React/TypeScript)

#### Install Dependencies

```bash
npm install
```

#### Start Development Server

```bash
npm run dev
```

The frontend will be available at `http://localhost:8080`.

#### Build for Production

```bash
npm run build
```

## Key Features and Usage Guide

### 1. Repository Analysis

1. In the main screen, click "Select Folder" to choose a local KMP project directory using the native file picker.

2. Alternatively, enter a GitHub repository URL (e.g., `https://github.com/username/repo`) or local path directly.

3. Click "Load Repository" or press Enter.

4. The application will:
   - Load the repository from the selected path
   - Analyze the KMP project structure
   - Run diagnostics on code quality
   - Generate AI-powered refactoring suggestions

### 2. Code Analysis and Suggestions

- **Modules View**: See all detected modules in your KMP project
- **Diagnostics**: View identified issues in the "Diagnostics" section (currently shows 1 diagnostic for WASM threading issues)
- **AI Suggestions**: Review AI-generated improvement suggestions (currently shows 4 suggestions including force unwrap replacements and large class splitting)
- **Shared Code Metrics**: View the percentage of shared code across platforms (currently ~83%)
- **Apply Fixes**: Click "Apply" on any suggestion to modify the code
- **Undo/Redo**: Use the undo/redo buttons to revert or reapply changes

### 3. GitHub Integration

1. Ensure GitHub token is configured in Settings.

2. For applying fixes via GitHub:
   - Click "Apply via GitHub" on a suggestion
   - Enter repository owner and name
   - The app creates a branch and pull request automatically

### 4. Server Management

- **Start Server**: Launches the embedded Ktor server on port 18999
- **Stop Server**: Shuts down the server
- **Server Status**: Shows current server state in the header
- **API Endpoints**: Full REST API available at `http://localhost:18999`
  - `/health` - Health check
  - `/state` - Current application state
  - `/modules` - Project modules
  - `/diagnostics` - Code diagnostics
  - `/refactors` - Refactoring suggestions
  - `/metrics` - Shared code metrics
  - `/previews` - Code previews

### 5. Configuration Management

- **API Keys**: Configure Groq API key for AI analysis
- **GitHub Token**: Enable GitHub operations
- **Settings**: Access via the "Settings" button in the top-right

## Current Status

### ✅ Working Features
- **Repository Loading**: Successfully loads and analyzes KMP projects
- **File Picker**: Native file selection dialogs for each platform
- **Diagnostics Engine**: Detects 1 diagnostic (WASM threading issues)
- **AI Suggestions**: Generates 4 refactoring suggestions
- **Embedded Server**: Complete REST API with all endpoints functional
- **Multiplatform UI**: Consistent interface across Desktop, Android, and Web
- **State Management**: Reactive UI updates with proper state flow

## Architecture

### Project Structure

```
backend/
├── composeApp/           # Compose Multiplatform app
│   ├── src/
│   │   ├── commonMain/   # Shared code
│   │   ├── desktopMain/  # Desktop-specific code
│   │   ├── androidMain/  # Android-specific code
│   │   └── wasmJsMain/   # Web-specific code
│   └── build.gradle.kts  # Compose app configuration
├── shared/               # Shared KMP module
│   └── src/
│       └── commonMain/   # Server and core logic
└── build.gradle.kts      # Root build configuration
```

### Key Components

- **DebugForgeViewModel**: Main UI state management
- **EmbeddedServer**: Ktor-based backend server
- **SecureStorage**: Encrypted credential storage
- **UndoManager**: Change tracking system
- **DebugForgeApiClient**: Client for server communication

## License

This project is licensed under the MIT License - see the LICENSE file for details.
