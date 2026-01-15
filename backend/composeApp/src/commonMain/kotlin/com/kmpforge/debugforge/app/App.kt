package com.kmpforge.debugforge.app

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.kmpforge.debugforge.platform.PlatformFileSystem
import com.kmpforge.debugforge.config.GitHubConfig
import com.kmpforge.debugforge.utils.DebugForgeLogger

@Composable
fun App() {
    DebugForgeLogger.debug("App", "App composable entered")
    val viewModel = remember { DebugForgeViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    DebugForgeLogger.debug("App", "uiState: $uiState")
    
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF7C3AED),
            secondary = Color(0xFF10B981),
            background = Color(0xFF0F172A),
            surface = Color(0xFF1E293B),
            onBackground = Color(0xFFE2E8F0),
            onSurface = Color(0xFFE2E8F0),
            error = Color(0xFFEF4444)
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val state = uiState) {
                is UiState.Idle -> RepoInputScreen(viewModel)
                is UiState.Settings -> SettingsScreen(viewModel)
                is UiState.Loading -> LoadingScreen(state.message)
                is UiState.Ready -> WorkspaceScreen(state, viewModel)
                is UiState.Error -> ErrorScreen(state.message, viewModel)
            }
        }
    }
}

@Composable
fun RepoInputScreen(viewModel: DebugForgeViewModel) {
    var repoPath by remember { mutableStateOf("d:\\Projects\\kotlin project\\kmp-forge-main\\backend") }
    val fileSystem = remember { PlatformFileSystem() }
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Header with settings button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { viewModel.navigateToSettings() }) {
                Text("⚙️", fontSize = 24.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Logo
        Surface(
            modifier = Modifier.size(80.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "K",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "DebugForge",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Text(
            "AI-Powered Kotlin Debugger",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Input card
        Surface(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Enter KMP Project Path or GitHub URL",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = repoPath,
                    onValueChange = { repoPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://github.com/user/repo or D:/Projects/my-kmp-project") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Browse button for folder picker
                OutlinedButton(
                    onClick = { 
                        scope.launch {
                            val selected = fileSystem.pickProjectFolder()
                            if (selected != null) {
                                repoPath = selected
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("📁 Browse Folder", modifier = Modifier.padding(8.dp))
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = { 
                        viewModel.loadRepo(repoPath) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = repoPath.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Scan Repository", modifier = Modifier.padding(8.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Backend status
        val isConnected by viewModel.isBackendConnected.collectAsState()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444))
                )
                Text(
                    if (isConnected) "Backend connected" else "Backend offline",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun LoadingScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }
}

@Composable
fun ErrorScreen(message: String, viewModel: DebugForgeViewModel) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "⚠️",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Error",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            message,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { viewModel.reset() }) {
            Text("Try Again")
        }
    }
}

@Composable
fun WorkspaceScreen(state: UiState.Ready, viewModel: DebugForgeViewModel) {
    val syncStatus by viewModel.syncStatus.collectAsState()
    val githubEnabled by viewModel.githubSyncEnabled.collectAsState()
    val undoStack by viewModel.undoManager.undoStack.collectAsState()
    val redoStack by viewModel.undoManager.redoStack.collectAsState()
    var showUndoHistory by remember { mutableStateOf(false) }
    
    val fileSystem = remember { com.kmpforge.debugforge.platform.PlatformFileSystem() }
    val scope = rememberCoroutineScope()
    var isPicking by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar with Change Project button
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        isPicking = true
                        scope.launch {
                            val selected = fileSystem.pickProjectFolder()
                            if (selected != null) {
                                viewModel.loadRepo(selected)
                            }
                            isPicking = false
                        }
                    },
                    enabled = !isPicking,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Change Project", modifier = Modifier.padding(4.dp))
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            val exportPath = fileSystem.pickProjectFolder()?.let { "$it/suggestions.txt" }
                            if (exportPath != null) {
                                val text = state.suggestions.joinToString("\n\n") { 
                                    "${it.title}\n${it.rationale}" 
                                }
                                fileSystem.writeFile(exportPath, text)
                                viewModel.setSyncStatus("✅ Suggestions exported to $exportPath")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Export", modifier = Modifier.padding(4.dp))
                }
            }
            
            Button(
                onClick = { viewModel.reset() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Close App", modifier = Modifier.padding(4.dp))
            }
        }
        // GitHub Sync Status Banner
        if (syncStatus != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (syncStatus!!.startsWith("✅")) {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                } else if (syncStatus!!.startsWith("❌")) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        syncStatus!!,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.clearSyncStatus() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("✕", fontSize = 16.sp)
                    }
                }
            }
        }
        
        Row(modifier = Modifier.weight(1f)) {
        // Left panel - Modules
        Surface(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Modules",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                LazyColumn {
                    items(state.modules) { module ->
                        ModuleItem(module)
                    }
                }
            }
        }
        
        // Center - Analysis Results with Tabs
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            var selectedTab by remember { mutableStateOf(0) }
            val tabs = listOf("Static Checks", "AI Recommendations")
            
            // Tab row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title)
                                Spacer(modifier = Modifier.width(4.dp))
                                val count = if (index == 0) state.diagnostics.size else state.suggestions.size
                                if (count > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            count.toString(),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            when (selectedTab) {
                0 -> {
                    // Static Checks (Diagnostics)
                    if (state.diagnostics.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("✅", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "No static analysis issues found!",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.diagnostics) { diagnostic ->
                                DiagnosticCard(diagnostic)
                            }
                        }
                    }
                }
                1 -> {
                    // AI Recommendations (Suggestions)
                    var searchQuery by remember { mutableStateOf("") }
                    
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search recommendations...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Text("🔍", fontSize = 16.sp) }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val filteredSuggestions = state.suggestions.filter {
                        searchQuery.isBlank() || 
                        it.title.contains(searchQuery, ignoreCase = true) || 
                        it.rationale.contains(searchQuery, ignoreCase = true)
                    }
                    
                    if (filteredSuggestions.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🤖", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (searchQuery.isBlank()) "No AI recommendations available" else "No recommendations match your search",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(filteredSuggestions) { suggestion ->
                                SuggestionCard(suggestion, viewModel, githubEnabled, state.repoPath)
                            }
                        }
                    }
                }
            }
        }
        
        // Right panel - Actions & Export
        Surface(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Actions",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Export button
                Button(
                    onClick = {
                        scope.launch {
                            val exportPath = fileSystem.pickProjectFolder()?.let { "$it/analysis_report.txt" }
                            if (exportPath != null) {
                                val report = buildString {
                                    appendLine("=== DEBUGFORGE ANALYSIS REPORT ===")
                                    appendLine("Project: ${state.repoPath}")
                                    appendLine("Generated: ${java.time.LocalDateTime.now()}")
                                    appendLine()
                                    
                                    appendLine("=== STATIC CHECKS (${state.diagnostics.size}) ===")
                                    state.diagnostics.forEach { diag ->
                                        appendLine("• ${diag.severity.uppercase()}: ${diag.message}")
                                        appendLine("  File: ${diag.filePath}:${diag.line}")
                                        appendLine()
                                    }
                                    
                                    appendLine("=== AI RECOMMENDATIONS (${state.suggestions.size}) ===")
                                    state.suggestions.forEach { sugg ->
                                        appendLine("• ${sugg.title}")
                                        appendLine("  ${sugg.rationale}")
                                        appendLine()
                                    }
                                    
                                    appendLine("=== METRICS ===")
                                    appendLine("Shared Code: ${state.sharedCodePercent.toInt()}%")
                                    appendLine("Modules: ${state.modules.size}")
                                }
                                fileSystem.writeFile(exportPath, report)
                                viewModel.setSyncStatus("✅ Analysis report exported to $exportPath")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("📄 Export Report", modifier = Modifier.padding(4.dp))
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Metrics display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Project Metrics",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "📊 Shared Code: ${state.sharedCodePercent.toInt()}%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "📁 Modules: ${state.modules.size}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "🔍 Static Issues: ${state.diagnostics.size}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "🤖 AI Suggestions: ${state.suggestions.size}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
    
        // Bottom Undo Bar
        if (undoStack.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Undo info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "↩️",
                            fontSize = 16.sp
                        )
                        Column {
                            Text(
                                "${undoStack.count { !it.isUndone }} changes can be undone",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Last: ${undoStack.firstOrNull { !it.isUndone }?.suggestionTitle ?: "None"}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1
                            )
                        }
                    }
                    
                    // Undo/Redo buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // History button
                        OutlinedButton(
                            onClick = { showUndoHistory = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("📋 History", fontSize = 12.sp)
                        }
                        
                        // Redo button
                        OutlinedButton(
                            onClick = { viewModel.redoLastFix() },
                            enabled = redoStack.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("↪️ Redo", fontSize = 12.sp)
                        }
                        
                        // Undo button
                        Button(
                            onClick = { viewModel.undoLastFix() },
                            enabled = undoStack.any { !it.isUndone },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("↩️ Undo", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    
    // Undo History Dialog
    if (showUndoHistory) {
        AlertDialog(
            onDismissRequest = { showUndoHistory = false },
            title = { 
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Undo History")
                    Text(
                        "${undoStack.size} total",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(undoStack.take(20)) { fix ->
                        UndoHistoryItem(
                            fix = fix,
                            onUndo = {
                                viewModel.undoFix(fix.id)
                                showUndoHistory = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.clearUndoHistory()
                            showUndoHistory = false
                        }
                    ) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = { showUndoHistory = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

@Composable
fun UndoHistoryItem(fix: AppliedFix, onUndo: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (fix.isUndone) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (fix.isUndone) "↩️" else "📝",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        fix.suggestionTitle.removePrefix("🤖 "),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (fix.isUndone) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1
                    )
                }
                Text(
                    fix.filePath.substringAfterLast("/").substringAfterLast("\\"),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            if (!fix.isUndone) {
                Button(
                    onClick = onUndo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Undo", fontSize = 11.sp)
                }
            } else {
                Text(
                    "Undone",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
fun ModuleItem(module: ModuleDisplay) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📁", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    module.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${module.fileCount} files",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun DiagnosticCard(diagnostic: DiagnosticDisplay) {
    val severityColor = when (diagnostic.severity) {
        "ERROR" -> Color(0xFFEF4444)
        "WARNING" -> Color(0xFFF59E0B)
        else -> Color(0xFF3B82F6)
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(severityColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    diagnostic.severity,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = severityColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    diagnostic.category,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                diagnostic.message,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                diagnostic.filePath,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace
            )
            
            Text(
                "Line ${diagnostic.line}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun SuggestionCard(
    suggestion: SuggestionDisplay,
    viewModel: DebugForgeViewModel,
    githubEnabled: Boolean,
    repoPath: String
) {
    var showDiffDialog by remember { mutableStateOf(false) }
    var showGitHubDialog by remember { mutableStateOf(false) }
    var showApplyConfirm by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤖", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    suggestion.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                suggestion.rationale,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDiffDialog = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(8.dp),
                        enabled = true // Always enabled to show diff details
                    ) {
                        Text("View Diff", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { showApplyConfirm = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(8.dp),
                        enabled = !suggestion.afterCode.isNullOrEmpty()
                    ) {
                        Text("Apply", fontSize = 12.sp)
                    }
                }
                
                if (githubEnabled) {
                    Button(
                        onClick = { showGitHubDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("🔄 Sync to GitHub", fontSize = 12.sp)
                    }
                }
            }
        }
    }
    
    // Diff dialog - shows before/after code or informational message
    if (showDiffDialog) {
        val hasCodeFix = suggestion.afterCode != null && suggestion.afterCode.isNotBlank()
        val isInformational = !hasCodeFix
        
        AlertDialog(
            onDismissRequest = { showDiffDialog = false },
            title = { Text(suggestion.title) },
            text = {
                Column {
                    if (isInformational) {
                        // Show informational suggestion content
                        Text("RECOMMENDATION:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF64B5F6))
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    suggestion.rationale,
                                    fontSize = 12.sp,
                                    color = Color(0xFFD4D4D4),
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "ℹ️ This is a best practice recommendation that requires manual refactoring. " +
                                    "Please review your code and apply the suggested changes manually.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9E9E9E),
                                    lineHeight = 16.sp
                                )
                                if (suggestion.beforeCode != null && suggestion.beforeCode.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("AFFECTED CODE:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFFFB74D))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        suggestion.beforeCode,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFB0BEC5),
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // Show before/after code diff
                        Text("BEFORE:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFE57373))
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                suggestion.beforeCode ?: suggestion.rationale,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFD4D4D4),
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text("AFTER:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF81C784))
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                suggestion.afterCode!!,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFD4D4D4),
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDiffDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
    
    // Apply confirmation dialog with file path input
    if (showApplyConfirm) {
        var targetFilePath by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showApplyConfirm = false },
            title = { Text("Apply Fix?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This will apply the suggested fix:")
                    
                    Text(
                        suggestion.title,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        suggestion.rationale,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = targetFilePath,
                        onValueChange = { targetFilePath = it },
                        label = { Text("Target File Path", fontSize = 12.sp) },
                        placeholder = { Text("e.g. $repoPath/src/Main.kt", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    // Warning message
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "A backup will be created. You can undo this change.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetFilePath.isNotBlank()) {
                            viewModel.applyLocalFix(suggestion, targetFilePath)
                            showApplyConfirm = false
                        }
                    },
                    enabled = targetFilePath.isNotBlank()
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // GitHub sync dialog
    if (showGitHubDialog) {
        var owner by remember { mutableStateOf(GitHubConfig.DEFAULT_OWNER) }
        var repo by remember { mutableStateOf(GitHubConfig.DEFAULT_REPO) }
        var filePath by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showGitHubDialog = false },
            title = { Text("Sync Fix to GitHub") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = owner,
                        onValueChange = { owner = it },
                        label = { Text("Owner", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = repo,
                        onValueChange = { repo = it },
                        label = { Text("Repository", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = filePath,
                        onValueChange = { filePath = it },
                        label = { Text("File Path", fontSize = 12.sp) },
                        placeholder = { Text("src/main/kotlin/File.kt", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Text(
                        "This will create a branch and pull request with the fix.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (owner.isNotBlank() && repo.isNotBlank() && filePath.isNotBlank()) {
                            viewModel.applyFixViaGitHub(
                                owner = owner,
                                repo = repo,
                                filePath = filePath,
                                newContent = suggestion.afterCode ?: "",
                                fixDescription = suggestion.title
                            )
                            showGitHubDialog = false
                        }
                    },
                    enabled = owner.isNotBlank() && repo.isNotBlank() && filePath.isNotBlank()
                ) {
                    Text("Create PR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGitHubDialog = false }) {
                    Text("Cancel")
                }            }
        )
    }
}

@Composable
fun SettingsScreen(viewModel: DebugForgeViewModel) {
    var groqApiKey by remember { mutableStateOf("") }
    var githubToken by remember { mutableStateOf("") }
    var showGroqKey by remember { mutableStateOf(false) }
    var showGithubKey by remember { mutableStateOf(false) }
    
    // Load current config
    LaunchedEffect(Unit) {
        val config = viewModel.getApiConfig()
        groqApiKey = config["groqApiKey"] ?: ""
        githubToken = config["githubToken"] ?: ""
    }
    
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    val isBackendConnected by viewModel.isBackendConnected.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            IconButton(onClick = { viewModel.navigateBack() }) {
                Text("✕", fontSize = 20.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // API Configuration Section
        Text(
            "API Configuration",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Groq API Key
                Text(
                    "Groq API Key (for AI Analysis)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = groqApiKey,
                        onValueChange = { groqApiKey = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Enter your Groq API key") },
                        visualTransformation = if (showGroqKey) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true
                    )
                    
                    IconButton(onClick = { showGroqKey = !showGroqKey }) {
                        Text(if (showGroqKey) "🙈" else "👁️", fontSize = 20.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // GitHub Token
                Text(
                    "GitHub Personal Access Token (for PR creation)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = githubToken,
                        onValueChange = { githubToken = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Enter your GitHub token") },
                        visualTransformation = if (showGithubKey) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true
                    )
                    
                    IconButton(onClick = { showGithubKey = !showGithubKey }) {
                        Text(if (showGithubKey) "🙈" else "👁️", fontSize = 20.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                // Save Button
                Button(
                    onClick = {
                        viewModel.saveApiConfig(groqApiKey, githubToken)
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Configuration")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Server Control Button
                Button(
                    onClick = { 
                        if (isServerRunning) {
                            viewModel.stopServer()
                        } else {
                            viewModel.startServer()
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(if (isServerRunning) "Stop Server" else "Start Server")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Server Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isBackendConnected) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isBackendConnected) "Backend online" else "Backend offline", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Info Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "How to get API keys:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "• Groq API Key: Visit https://console.groq.com/ to get your API key for AI-powered code analysis",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    "• GitHub Token: Go to GitHub Settings → Developer settings → Personal access tokens to create a token with repo permissions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}