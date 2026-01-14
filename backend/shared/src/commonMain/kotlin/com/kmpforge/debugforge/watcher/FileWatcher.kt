package com.kmpforge.debugforge.watcher

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents a file system change event.
 */
@Serializable
data class FileChangeEvent(
    val path: String,
    val changeType: ChangeType,
    val timestamp: Instant
)

@Serializable
enum class ChangeType {
    CREATED,
    MODIFIED,
    DELETED,
    RENAMED
}

/**
 * Interface for watching file system changes.
 * Platform-specific implementations provide actual file watching.
 */
interface FileWatcher {
    /**
     * Flow of file change events.
     */
    val events: SharedFlow<FileChangeEvent>
    
    /**
     * Starts watching the specified paths.
     */
    suspend fun watch(paths: List<String>)
    
    /**
     * Stops watching all paths.
     */
    suspend fun stopAll()
    
    /**
     * Stops watching a specific path.
     */
    suspend fun stop(path: String)
    
    /**
     * Returns list of currently watched paths.
     */
    fun getWatchedPaths(): List<String>
}

/**
 * Filters for file watching.
 */
data class WatchFilter(
    /**
     * File extensions to include (e.g., "kt", "kts").
     * Empty list means include all.
     */
    val includeExtensions: Set<String> = emptySet(),
    
    /**
     * File extensions to exclude.
     */
    val excludeExtensions: Set<String> = setOf("class", "jar", "lock"),
    
    /**
     * Directory names to exclude.
     */
    val excludeDirectories: Set<String> = setOf(
        "build", ".gradle", ".idea", "node_modules", 
        "__pycache__", ".git", "target"
    ),
    
    /**
     * Minimum interval between events for the same file (debounce).
     */
    val debounceMs: Long = 100
) {
    fun matches(path: String): Boolean {
        val normalizedPath = path.replace('\\', '/')
        
        // Check excluded directories
        for (excluded in excludeDirectories) {
            if (normalizedPath.contains("/$excluded/") || normalizedPath.startsWith("$excluded/")) {
                return false
            }
        }
        
        // Check extension
        val extension = normalizedPath.substringAfterLast('.', "")
        
        if (extension in excludeExtensions) {
            return false
        }
        
        if (includeExtensions.isNotEmpty() && extension !in includeExtensions) {
            return false
        }
        
        return true
    }
}

/**
 * Debounced file watcher that batches rapid changes.
 */
class DebouncedFileWatcher(
    private val delegate: FileWatcher,
    private val filter: WatchFilter = WatchFilter()
) : FileWatcher {
    
    private val _events = MutableSharedFlow<FileChangeEvent>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val events: SharedFlow<FileChangeEvent> = _events.asSharedFlow()
    
    private val recentEvents = mutableMapOf<String, Long>()
    
    override suspend fun watch(paths: List<String>) {
        delegate.watch(paths)
        
        // Collect from delegate and apply filtering/debouncing
        delegate.events.collect { event ->
            if (filter.matches(event.path)) {
                val now = event.timestamp.toEpochMilliseconds()
                val lastEventTime = recentEvents[event.path] ?: 0L
                
                if (now - lastEventTime >= filter.debounceMs) {
                    recentEvents[event.path] = now
                    _events.emit(event)
                    
                    // Clean up old entries periodically
                    if (recentEvents.size > 1000) {
                        val cutoff = now - (filter.debounceMs * 10)
                        val keysToRemove = recentEvents.entries
                            .filter { it.value < cutoff }
                            .map { it.key }
                        keysToRemove.forEach { recentEvents.remove(it) }
                    }
                }
            }
        }
    }
    
    override suspend fun stopAll() {
        delegate.stopAll()
        recentEvents.clear()
    }
    
    override suspend fun stop(path: String) {
        delegate.stop(path)
    }
    
    override fun getWatchedPaths(): List<String> = delegate.getWatchedPaths()
}

/**
 * Aggregates multiple file change events for batch processing.
 */
class ChangeAggregator {
    private val pendingChanges = mutableMapOf<String, FileChangeEvent>()
    private var lastFlushTime: Long = 0
    
    /**
     * Adds a change event.
     */
    fun add(event: FileChangeEvent) {
        val existing = pendingChanges[event.path]
        
        // Merge logic: keep the most significant change type
        val mergedEvent = if (existing != null) {
            when {
                event.changeType == ChangeType.DELETED -> event
                existing.changeType == ChangeType.CREATED && event.changeType == ChangeType.MODIFIED -> existing
                else -> event
            }
        } else {
            event
        }
        
        pendingChanges[event.path] = mergedEvent
    }
    
    /**
     * Returns and clears all pending changes.
     */
    fun flush(): List<FileChangeEvent> {
        val changes = pendingChanges.values.toList()
        pendingChanges.clear()
        lastFlushTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        return changes
    }
    
    /**
     * Returns pending changes without clearing.
     */
    fun peek(): List<FileChangeEvent> = pendingChanges.values.toList()
    
    /**
     * Returns true if there are pending changes.
     */
    fun hasPending(): Boolean = pendingChanges.isNotEmpty()
    
    /**
     * Returns count of pending changes.
     */
    fun pendingCount(): Int = pendingChanges.size
}
