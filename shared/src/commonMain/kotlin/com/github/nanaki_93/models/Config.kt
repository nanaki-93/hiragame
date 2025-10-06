package com.github.nanaki_93.models

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val apiUrl: String,
    val appName: String,
    val debugMode: Boolean,
    val theme: ThemeConfig
)

@Serializable
data class ThemeConfig(
    val primaryColor: String,
    val darkMode: Boolean
)