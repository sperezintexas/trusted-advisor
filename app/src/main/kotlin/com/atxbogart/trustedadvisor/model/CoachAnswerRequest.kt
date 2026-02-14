package com.atxbogart.trustedadvisor.model

data class CoachAnswerRequest(
    val userId: String = "default",
    val questionId: String,
    val selectedLetter: ChoiceLetter
)
