// backend/composeApp/src/commonMain/kotlin/com/kmpforge/debugforge/config/GitHubConfig.kt
package com.kmpforge.debugforge.config

object GitHubConfig {
    var GITHUB_TOKEN: String = "ghp_your_token_here"
    const val ENABLE_GITHUB_SYNC = true
    var DEFAULT_OWNER: String = "yourusername"
    var DEFAULT_REPO: String = "yourrepo"
}