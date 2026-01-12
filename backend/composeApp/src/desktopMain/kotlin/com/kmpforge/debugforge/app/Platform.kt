package com.kmpforge.debugforge.app

actual fun getTempDir(): String = System.getProperty("java.io.tmpdir")

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
