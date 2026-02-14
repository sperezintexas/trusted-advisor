package com.atxbogart.trustedadvisor.model

/**
 * Question payload for a practice exam session — no correct answer or explanation
 * so the client cannot cheat before submitting.
 */
data class PracticeExamQuestion(
    val id: String,
    val question: String,
    val choices: List<CoachChoice>
)

data class PracticeSessionResponse(
    val questions: List<PracticeExamQuestion>,
    val totalMinutes: Int
)

data class ScoreAnswerRequest(
    val questionId: String,
    val selectedLetter: String
)

data class ScoreRequest(
    val answers: List<ScoreAnswerRequest>,
    val userId: String? = null,
    val save: Boolean = false
)

data class ScoreResponse(
    val correct: Int,
    val total: Int,
    val percentage: Double,
    val passed: Boolean,
    val passingPercentage: Int
)
