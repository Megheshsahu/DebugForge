package com.kmpforge.debugforge.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

/**
 * JS implementation of DatabaseDriverFactory.
 * Uses web worker driver for browser environments.
 * Note: This requires a web worker setup in the browser.
 */
actual object DatabaseDriverFactory {
    
    actual fun createDriver(path: String?): SqlDriver {
        // For JS, we use the web worker driver
        // The path parameter is used as the database name in IndexedDB
        return WebWorkerDriver(
            Worker(
                js("new URL('./sqljs.worker.js', import.meta.url)") as String
            )
        )
    }
}
