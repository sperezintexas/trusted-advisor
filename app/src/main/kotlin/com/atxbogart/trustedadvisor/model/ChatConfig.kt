package com.atxbogart.trustedadvisor.model

data class ChatConfig(
    val tools: Map<String, Boolean> = mapOf(
        "webSearch" to true,
        "portfolio" to true,
        "coveredCallRecs" to true
    ),
    val context: Map<String, String> = mapOf(
        "riskProfile" to "medium",
        "personaId" to ""
    )
)