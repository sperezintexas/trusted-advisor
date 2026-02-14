package com.atxbogart.trustedadvisor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.time.ZoneOffset

@Document(collection = "coachQuestions")
data class CoachQuestion(
    @Id
    val id: String? = null,
    val examCode: ExamCode,
    val question: String,
    val choices: List<CoachChoice>,
    val correctLetter: ChoiceLetter,
    val explanation: String,
    val outlineReference: String? = null,
    val topic: String? = null,
    val difficulty: Difficulty? = null,
    val source: String? = null,
    val active: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    val updatedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
)

data class CoachChoice(
    val letter: ChoiceLetter,
    val text: String
)

enum class ChoiceLetter {
    A, B, C, D
}

enum class Difficulty {
    easy, medium, hard
}
