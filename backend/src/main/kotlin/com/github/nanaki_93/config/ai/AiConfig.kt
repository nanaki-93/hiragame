package com.github.nanaki_93.config.ai

import org.springframework.http.HttpEntity
import org.springframework.stereotype.Service

@Service
interface AiConfig {
    fun getApiUrl(): String
    fun getRequest(prompt: String): HttpEntity<Map<String, Any>>
}