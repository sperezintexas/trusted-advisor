package com.atxbogart.trustedadvisor.model

data class ChatResponse(
    val response: String,
    val usage: GrokUsage? = null
)