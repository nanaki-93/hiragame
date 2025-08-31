package com.github.nanaki_93.service.ai

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("ollama")
class OllamaOfflineService(
    ollamaChatModel: OllamaChatModel,
) : AiService {
    private val logger = LoggerFactory.getLogger(OllamaOfflineService::class.java)
    private val chatClient = ChatClient.create(ollamaChatModel)

    override fun callApi(prompt: String): String =
        runCatching {
            chatClient.prompt()
                .user(prompt)
                .options(
                    ChatOptions.builder()
                        .temperature(0.1)
                        .topK(40)
                        .topP(0.9)
                        .build()
                )
                .call()
                .content()
        }
            .also { logger.info("Received response from Spring AI: ${it.getOrNull()?.take(100)}...") }
            .onFailure { logger.error("Error calling Ollama via Spring AI: ${it.message}") }
            .getOrDefault("Failed to call Ollama via Spring AI")

}