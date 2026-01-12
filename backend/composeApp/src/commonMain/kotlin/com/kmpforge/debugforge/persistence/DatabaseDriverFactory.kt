package com.kmpforge.debugforge.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * JVM implementation for database driver factory.
 */
object DatabaseDriverFactory {
    /**
     * Creates a SQLDriver for the current platform.
     *
     * @param path Path to the database file. If null, uses in-memory database.
     */
    fun createDriver(path: String?): SqlDriver {
        val url = if (path != null) {
            val file = File(path)
            file.parentFile?.mkdirs()
            "jdbc:sqlite:$path"
        } else {
            JdbcSqliteDriver.IN_MEMORY
        }

        return JdbcSqliteDriver(url)
    }
}
