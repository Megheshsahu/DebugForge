package com.kmpforge.debugforge.config

actual fun getEnvVariable(name: String): String? = System.getenv(name)
