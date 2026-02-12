package com.atxbogart.trustedadvisor.model

import java.time.LocalDateTime
import java.time.ZoneOffset

data class ChatMessage(
    val role: String,  // "user" or "assistant"
    val content: String,
    val timestamp: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
)