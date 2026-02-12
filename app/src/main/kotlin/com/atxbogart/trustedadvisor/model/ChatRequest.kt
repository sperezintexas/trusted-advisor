package com.atxbogart.trustedadvisor.model

data class ChatRequest(
    val message: String,
    val personaId: String? = null,
    val userId: String = "default"
)