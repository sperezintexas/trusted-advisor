package com.atxbogart.trustedadvisor.model

data class CheckAnswerResult(
    val correct: Boolean,
    val correctLetter: ChoiceLetter,
    val explanation: String
)
