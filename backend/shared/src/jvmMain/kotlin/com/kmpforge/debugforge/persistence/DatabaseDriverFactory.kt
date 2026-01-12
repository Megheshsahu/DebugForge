package com.kmpforge.debugforge.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * JVM actual implementation for DatabaseDriverFactory.
 */
actual object DatabaseDriverFactory {
    actual fun createDriver(path: String?): SqlDriver {
        val url = if (path != null) {
            val file = File(path)
            file.parentFile?.mkdirs()
            "jdbc:sqlite:$path"
        } else {
            JdbcSqliteDriver.IN_MEMORY
        }
        
        return JdbcSqliteDriver(url)
    }
    
    /**
     * Gets the default database path for the user's home directory.
     */
    actual fun getDefaultDatabasePath(): String {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".debugforge")
        appDir.mkdirs()
        return File(appDir, "debugforge.db").absolutePath
    }
}
