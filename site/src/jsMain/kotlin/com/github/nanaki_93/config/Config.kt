package com.github.nanaki_93.config

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.nanaki_93.models.AppConfig
import com.github.nanaki_93.models.ThemeConfig
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json


object ConfigLoader {
    private var _config: AppConfig? = null

    suspend fun loadConfig(): AppConfig {
        if (_config == null) {
            try {
                val response = window.fetch("/hiragame/config.json").await()
                val configText = response.text().await()
                _config = Json.decodeFromString<AppConfig>(configText)
                println("Config loaded")
                println(_config)
            } catch (e: Exception) {
                console.error("Failed to load config:", e)
                // Provide default config
                _config = AppConfig(
                    apiUrl = "http://localhost:8080",
                    appName = "Default App",
                    debugMode = false,
                    theme = ThemeConfig("#000000", false)
                )
            }
        }
        return _config!!
    }

    fun getDefaultConfig(): AppConfig = AppConfig(
        apiUrl = "http://localhost:9999",
        appName = "Default App",
        debugMode = false,
        theme = ThemeConfig("#000000", true)
    )


}

