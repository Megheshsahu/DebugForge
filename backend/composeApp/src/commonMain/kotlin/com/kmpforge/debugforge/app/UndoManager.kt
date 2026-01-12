package com.kmpforge.debugforge.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Platform-specific current time in milliseconds
expect fun currentTimeMillis(): Long

/**
 * Represents a single applied fix that can be undone
 */
data class AppliedFix(
    val id: String,
    val filePath: String,
    val originalContent: String,
    val newContent: String,
    val suggestionTitle: String,
    val appliedAt: Long = currentTimeMillis(),
    val isUndone: Boolean = false
)

/**
 * Result of an undo operation
 */
sealed class UndoResult {
    data class Success(val fix: AppliedFix, val message: String) : UndoResult()
    data class Failed(val error: String) : UndoResult()
    data object NothingToUndo : UndoResult()
}

/**
 * Manages the undo/redo stack for applied fixes
 * Keeps track of all changes and allows reverting them
 */
class UndoManager {
    
    companion object {
        private const val MAX_UNDO_HISTORY = 50
    }
    
    // Stack of applied fixes (most recent first)
    private val _undoStack = MutableStateFlow<List<AppliedFix>>(emptyList())
    val undoStack: StateFlow<List<AppliedFix>> = _undoStack.asStateFlow()
    
    // Redo stack for undone fixes
    private val _redoStack = MutableStateFlow<List<AppliedFix>>(emptyList())
    val redoStack: StateFlow<List<AppliedFix>> = _redoStack.asStateFlow()
    
    // Current status message
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()
    
    /**
     * Check if there are changes that can be undone
     */
    fun canUndo(): Boolean = _undoStack.value.isNotEmpty()
    
    /**
     * Check if there are changes that can be redone
     */
    fun canRedo(): Boolean = _redoStack.value.isNotEmpty()
    
    /**
     * Get the count of undoable changes
     */
    fun undoCount(): Int = _undoStack.value.count { !it.isUndone }
    
    /**
     * Record a fix before applying it
     * Call this BEFORE writing the new content to the file
     */
    fun recordFix(
        filePath: String,
        originalContent: String,
        newContent: String,
        suggestionTitle: String
    ): AppliedFix {
        val fix = AppliedFix(
            id = "fix-${currentTimeMillis()}-${filePath.hashCode().toString(16)}",
            filePath = filePath,
            originalContent = originalContent,
            newContent = newContent,
            suggestionTitle = suggestionTitle
        )
        
        // Add to undo stack (most recent first)
        val currentStack = _undoStack.value.toMutableList()
        currentStack.add(0, fix)
        
        // Keep only last MAX_UNDO_HISTORY items
        if (currentStack.size > MAX_UNDO_HISTORY) {
            _undoStack.value = currentStack.take(MAX_UNDO_HISTORY)
        } else {
            _undoStack.value = currentStack
        }
        
        // Clear redo stack when a new fix is applied
        _redoStack.value = emptyList()
        
        _statusMessage.value = "📝 Backup created for: ${filePath.substringAfterLast("/").substringAfterLast("\\")}"
        
        return fix
    }
    
    /**
     * Get the most recent fix that can be undone
     */
    fun peekUndo(): AppliedFix? = _undoStack.value.firstOrNull { !it.isUndone }
    
    /**
     * Mark the most recent fix as undone and return it
     * The actual file restoration should be done by the caller
     */
    fun popUndo(): AppliedFix? {
        val stack = _undoStack.value.toMutableList()
        val fixToUndo = stack.firstOrNull { !it.isUndone } ?: return null
        
        // Mark as undone
        val index = stack.indexOf(fixToUndo)
        stack[index] = fixToUndo.copy(isUndone = true)
        _undoStack.value = stack
        
        // Add to redo stack
        val redoStack = _redoStack.value.toMutableList()
        redoStack.add(0, fixToUndo)
        _redoStack.value = redoStack
        
        return fixToUndo
    }
    
    /**
     * Get the most recent undone fix that can be redone
     */
    fun peekRedo(): AppliedFix? = _redoStack.value.firstOrNull()
    
    /**
     * Remove from redo stack and return the fix to reapply
     */
    fun popRedo(): AppliedFix? {
        val stack = _redoStack.value.toMutableList()
        val fixToRedo = stack.firstOrNull() ?: return null
        stack.removeAt(0)
        _redoStack.value = stack
        
        // Mark as not undone in undo stack
        val undoStack = _undoStack.value.toMutableList()
        val index = undoStack.indexOfFirst { it.id == fixToRedo.id }
        if (index >= 0) {
            undoStack[index] = fixToRedo.copy(isUndone = false)
            _undoStack.value = undoStack
        }
        
        return fixToRedo
    }
    
    /**
     * Get recent undo history for display
     */
    fun getRecentHistory(limit: Int = 10): List<AppliedFix> {
        return _undoStack.value.take(limit)
    }
    
    /**
     * Clear a specific fix from history by ID
     */
    fun clearFix(fixId: String) {
        _undoStack.value = _undoStack.value.filter { it.id != fixId }
        _redoStack.value = _redoStack.value.filter { it.id != fixId }
    }
    
    /**
     * Clear all undo/redo history
     */
    fun clearAll() {
        _undoStack.value = emptyList()
        _redoStack.value = emptyList()
        _statusMessage.value = "🗑️ Undo history cleared"
    }
    
    /**
     * Get a formatted summary of the undo stack
     */
    fun getSummary(): String {
        val active = _undoStack.value.count { !it.isUndone }
        val total = _undoStack.value.size
        return "$active undoable changes ($total total in history)"
    }
    
    fun clearStatus() {
        _statusMessage.value = null
    }
}
