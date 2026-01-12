package com.kmpforge.debugforge.persistence

import app.cash.sqldelight.db.SqlDriver

/**
 * Native implementation of DatabaseDriverFactory.
 * Uses SQLite native driver.
 * 
 * Note: SQLDelight native driver needs platform-specific configuration.
 * This is a stub implementation - for full support, add the appropriate
 * SQLDelight native driver dependency for each native target.
 */
actual object DatabaseDriverFactory {
    
    actual fun createDriver(path: String?): SqlDriver {
        // Native SQLite driver
        // TODO: Add proper native driver implementation when needed
        // For now, this throws as native targets primarily use the shared code analysis,
        // not the database persistence layer
        throw UnsupportedOperationException(
            "Native database driver not yet implemented. " +
            "Use InMemoryRepoIndexDao for native targets."
        )
    }
}
