# DebugForge

DebugForge is a Kotlin Multiplatform application for analyzing and fixing issues in Kotlin Multiplatform projects. It provides AI-powered code analysis, automated refactoring suggestions, and seamless integration with GitHub for collaborative development.

## Features

- **Multiplatform Support**: Runs on Desktop (Windows), Android, and Web (WASM)
- **AI-Powered Analysis**: Uses Grok AI for intelligent code suggestions
- **GitHub Integration**: Clone repositories, create branches, and submit pull requests
- **Undo/Redo System**: Track and revert applied fixes
- **Secure Storage**: Encrypted storage for API keys and tokens
- **Real-time Diagnostics**: Comprehensive analysis of KMP project structure
- **Embedded Server**: Built-in Ktor server for backend operations

## Prerequisites

### System Requirements
- **Operating System**: Windows 10/11 (for Desktop), Android 8.0+ (for Android)
- **Java Development Kit (JDK)**: JDK 17 or later
- **Android Studio**: For Android development and testing (optional for Desktop-only)
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

### 2. Build the Project

The project uses Gradle with Kotlin DSL. Ensure you have JDK 17+ installed.

#### On Windows (PowerShell):

```powershell
# Navigate to backend directory
cd backend

# Build all targets
./gradlew build

# Or build specific targets
./gradlew :composeApp:assembleDebug  # Android debug APK
./gradlew :composeApp:createDistributable  # Desktop distributable
```

#### On Linux/macOS:

```bash
cd backend
./gradlew build
```

### 3. Desktop Application Setup

#### Build the Desktop Distributable

```bash
cd backend
./gradlew :composeApp:createDistributable
```

This creates a native Windows executable at:
`backend/composeApp/build/compose/binaries/main/app/DebugForge/DebugForge.exe`

#### Run the Desktop Application

1. Navigate to the build directory:
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

6. Click "Start Server" to launch the embedded backend server on port 18999.

### 4. Android Application Setup

#### Prerequisites for Android
- Android Studio Arctic Fox or later
- Android SDK with API level 24+
- Connected Android device or emulator

#### Build and Install APK

1. Open the project in Android Studio:
   - File > Open > Select the `backend` directory

2. Wait for Gradle sync to complete.

3. Build the debug APK:
   ```bash
   cd backend
   ./gradlew :composeApp:assembleDebug
   ```

4. Install on device/emulator:
   ```bash
   ./gradlew :composeApp:installDebug
   ```

5. Or install manually:
   - Locate APK at: `backend/composeApp/build/outputs/apk/debug/composeApp-debug.apk`
   - Transfer to device and install

#### Run on Android Device

1. Launch the DebugForge app on your Android device.

2. Grant necessary permissions if prompted.

3. Tap "Settings" to configure API keys.

4. Enter your Groq API key and GitHub token.

5. Tap "Save Configuration".

6. Tap "Start Server" to launch the backend.

### 5. Web Application (WASM) Setup

#### Build for Web

```bash
cd backend
./gradlew :composeApp:wasmJsBrowserDistribution
```

#### Serve the Web Application

The built files will be in `backend/composeApp/build/dist/wasmJs/productionExecutable/`

Use any static web server to serve these files:

```bash
cd backend/composeApp/build/dist/wasmJs/productionExecutable
python -m http.server 8080
```

Open `http://localhost:8080` in a modern browser.

## Key Features and Usage Guide

### 1. Repository Analysis

1. In the main screen, enter a GitHub repository URL (e.g., `https://github.com/username/repo`) or local path.

2. Click "Load Repository" or press Enter.

3. The application will:
   - Clone the repository (for GitHub URLs)
   - Analyze the KMP project structure
   - Run diagnostics on code quality
   - Generate AI-powered suggestions

### 2. Code Analysis and Suggestions

- **Diagnostics**: View identified issues in the "Diagnostics" section
- **AI Suggestions**: Review AI-generated improvement suggestions
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

### 5. Configuration Management

- **API Keys**: Configure Groq API key for AI analysis
- **GitHub Token**: Enable GitHub operations
- **Settings**: Access via the "Settings" button in the top-right

## Troubleshooting

### Common Issues

#### 1. Build Failures
- Ensure JDK 17+ is installed and JAVA_HOME is set
- Run `./gradlew clean` before rebuilding
- Check for conflicting JAR files in build directories

#### 2. Server Won't Start
- Verify API keys are entered and saved
- Check that port 18999 is not in use by another application
- Ensure firewall allows local connections

#### 3. Android Installation Issues
- Enable USB debugging on device
- Accept USB debugging authorization prompt
- Ensure device storage has sufficient space

#### 4. GitHub Operations Fail
- Verify token has correct scopes
- Check repository access permissions
- Ensure repository exists and is accessible

#### 5. Desktop Application Won't Launch
- Ensure all JAR dependencies are present in the app directory
- Check DebugForge.cfg file exists and is valid
- Try running from command line to see error messages

### Logs and Debugging

- Desktop: Check console output when running from terminal
- Android: Use `adb logcat` or Android Studio's logcat
- Server: Check port 18999 with `netstat -ano | findstr :18999`

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

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make changes and test on all targets
4. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.
