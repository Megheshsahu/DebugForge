package com.kmpforge.debugforge.app

import kotlin.js.Date

actual fun getTempDir(): String = "/tmp"

actual fun currentTimeMillis(): Long = Date.now().toLong()
