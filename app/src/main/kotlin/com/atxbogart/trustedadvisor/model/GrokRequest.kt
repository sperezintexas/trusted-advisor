package com.atxbogart.trustedadvisor.model

data class GrokRequest(
    val model: String = "grok-4",
    val messages: List<ChatMessage>,
    val tools: List<Any>? = null,  // later
    val temperature: Double = 0.7,
    val max_tokens: Int = 2048
)