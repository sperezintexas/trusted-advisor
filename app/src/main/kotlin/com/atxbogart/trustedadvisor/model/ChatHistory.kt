package com.atxbogart.trustedadvisor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.ZoneOffset
import java.time.LocalDateTime

@Document(collection = "chatHistory")
data class ChatHistory(
    @Id
    val id: String? = null,
    val userId: String,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val updatedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
)