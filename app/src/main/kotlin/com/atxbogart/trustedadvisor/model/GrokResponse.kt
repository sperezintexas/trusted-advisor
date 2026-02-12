package com.atxbogart.trustedadvisor.model

data class GrokResponse(
    val choices: List<GrokChoice>
)

data class GrokChoice(
    val message: GrokMessage
)

data class GrokMessage(
    val role: String,
    val content: String
)