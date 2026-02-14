package com.atxbogart.trustedadvisor.model

data class GrokResponse(
    val choices: List<GrokChoice>,
    val usage: GrokUsage? = null
)

data class GrokChoice(
    val message: GrokMessage
)

data class GrokMessage(
    val role: String,
    val content: String
)

data class GrokUsage(
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null
)