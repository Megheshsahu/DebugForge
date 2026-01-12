package com.kmpforge.debugforge.persistence

import app.cash.sqldelight.db.SqlDriver

/**
 * Platform-specific database driver factory.
 */
expect object DatabaseDriverFactory {
    /**
     * Creates a SQLDriver for the current platform.
     *
     * @param path Path to the database file. If null, uses in-memory database.
     */
    fun createDriver(path: String?): SqlDriver
    
    /**
     * Gets the default database path for the platform.
     */
    fun getDefaultDatabasePath(): String
}
