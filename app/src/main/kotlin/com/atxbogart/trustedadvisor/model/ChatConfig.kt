package com.atxbogart.trustedadvisor.model

data class ChatConfig(
    val debug: Boolean = false,
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

/** Partial update for PUT /api/chat/config; null means "leave unchanged". */
data class ChatConfigUpdate(
    val debug: Boolean? = null,
    val tools: Map<String, Boolean>? = null,
    val context: Map<String, String>? = null
)