package com.kmpforge.debugforge.app

import android.os.Environment

actual fun getTempDir(): String = Environment.getExternalStorageDirectory().absolutePath

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
