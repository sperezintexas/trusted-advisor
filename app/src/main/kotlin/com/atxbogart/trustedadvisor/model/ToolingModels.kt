package com.atxbogart.trustedadvisor.model

data class ChatCitation(
    val url: String,
    val title: String? = null,
    val sourceType: String? = null
)

data class ChatToolEvent(
    val type: String,
    val name: String? = null,
    val status: String? = null,
    val callId: String? = null,
    val arguments: String? = null,
    val output: String? = null
)

data class ToolExecutionPolicy(
    val webSearchEnabled: Boolean,
    val yahooFinanceEnabled: Boolean,
    val internalActionsEnabled: Boolean
)

data class ToolCapableChatResult(
    val response: String,
    val usage: GrokUsage? = null,
    val citations: List<ChatCitation> = emptyList(),
    val toolEvents: List<ChatToolEvent> = emptyList()
)
