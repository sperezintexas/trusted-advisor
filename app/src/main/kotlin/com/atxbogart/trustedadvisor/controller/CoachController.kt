package com.atxbogart.trustedadvisor.controller

import com.atxbogart.trustedadvisor.model.CheckAnswerResult
import com.atxbogart.trustedadvisor.model.ChoiceLetter
import com.atxbogart.trustedadvisor.model.CoachAnswerRequest
import com.atxbogart.trustedadvisor.model.CoachExam
import com.atxbogart.trustedadvisor.model.CoachExamAttempt
import com.atxbogart.trustedadvisor.model.CoachQuestion
import com.atxbogart.trustedadvisor.model.CoachUserProgress
import com.atxbogart.trustedadvisor.model.ExamCode
import com.atxbogart.trustedadvisor.model.PracticeSessionResponse
import com.atxbogart.trustedadvisor.model.ScoreRequest
import com.atxbogart.trustedadvisor.model.ScoreResponse
import com.atxbogart.trustedadvisor.service.CoachService
import org.springframework.dao.DataAccessException
import org.springframework.http.ResponseEntity
import com.atxbogart.trustedadvisor.config.ApiKeyPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/coach")
@CrossOrigin(origins = ["http://localhost:3000"])
class CoachController(private val coachService: CoachService) {

    private fun userIdOrUnauthorized(principal: ApiKeyPrincipal?): String? =
        principal?.userId

    @GetMapping("/exams")
    fun getExams(): ResponseEntity<List<CoachExam>> =
        try {
            ResponseEntity.ok(coachService.getExams())
        } catch (e: DataAccessException) {
            ResponseEntity.ok(emptyList())
        }

    @GetMapping("/questions/{examCode}")
    fun getRandomQuestion(
        @PathVariable examCode: String,
        @RequestParam(required = false) excludeIds: List<String>? = null
    ): ResponseEntity<CoachQuestion?> {
        val code = parseExamCode(examCode) ?: return ResponseEntity.badRequest().build()
        return try {
            val q = coachService.getRandomQuestion(code, excludeIds ?: emptyList())
            if (q != null) ResponseEntity.ok(q) else ResponseEntity.notFound().build()
        } catch (e: DataAccessException) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/exams/{examCode}/check")
    fun checkAnswer(
        @PathVariable examCode: String,
        @RequestParam questionId: String,
        @RequestParam selectedLetter: String
    ): ResponseEntity<CheckAnswerResult> {
        val code = parseExamCode(examCode) ?: return ResponseEntity.badRequest().build()
        val letter = parseChoiceLetter(selectedLetter) ?: return ResponseEntity.badRequest().build()
        return coachService.checkAnswer(code, questionId, letter)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @PostMapping("/answers/{examCode}")
    fun recordAnswer(
        @PathVariable examCode: String,
        @RequestBody request: CoachAnswerRequest,
        @AuthenticationPrincipal principal: ApiKeyPrincipal?
    ): ResponseEntity<RecordAnswerResponse> {
        val userId = userIdOrUnauthorized(principal) ?: return ResponseEntity.status(401).build()
        val code = parseExamCode(examCode) ?: return ResponseEntity.badRequest().build()
        val ok = coachService.recordAnswer(userId, request.questionId, request.selectedLetter)
        return ResponseEntity.ok(RecordAnswerResponse(correct = ok))
    }

    @GetMapping("/progress/{examCode}")
    fun getProgress(
        @PathVariable examCode: String,
        @AuthenticationPrincipal principal: ApiKeyPrincipal?
    ): ResponseEntity<CoachUserProgress> {
        val userId = userIdOrUnauthorized(principal) ?: return ResponseEntity.status(401).build()
        val code = parseExamCode(examCode) ?: return ResponseEntity.badRequest().build()
        return try {
            ResponseEntity.ok(coachService.getProgress(userId, code))
        } catch (e: DataAccessException) {
            ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping("/exams/{examCode}/practice-session")
    fun getPracticeSession(
        @PathVariable examCode: String,
        @RequestParam count: Int = 75
    ): ResponseEntity<PracticeSessionResponse> {
        val code = parseExamCode(examCode) ?: return ResponseEntity.badRequest().build()
        return try {
            val session = coachService.getPracticeExamQuestions(code, count.coerceIn(1, 200))
            ResponseEntity.ok(session)
        } catch (e: DataAccessException) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/history")
    fun getAttemptHistory(@AuthenticationPrincipal principal: ApiKeyPrincipal?): ResponseEntity<List<CoachExamAttempt>> {
        val userId = userIdOrUnauthorized(principal) ?: return ResponseEntity.status(401).build()
        return try {
            ResponseEntity.ok(coachService.getAttemptHistory(userId))
        } catch (e: DataAccessException) {
            ResponseEntity.ok(emptyList())
        }
    }

    @GetMapping("/exams/{examCode}/history")
    fun getExamAttemptHistory(
        @PathVariable examCode: String,
        @AuthenticationPrincipal principal: ApiKeyPrincipal?
    ): ResponseEntity<List<CoachExamAttempt>> {
        val userId = userIdOrUnauthorized(principal) ?: return ResponseEntity.status(401).build()
        val code = parseExamCode(examCode) ?: return ResponseEntity.badRequest().build()
        return try {
            ResponseEntity.ok(coachService.getAttemptHistoryByExam(userId, code))
        } catch (e: DataAccessException) {
            ResponseEntity.ok(emptyList())
        }
    }

    @PostMapping("/exams/{examCode}/score")
    fun scorePracticeExam(
        @PathVariable examCode: String,
        @RequestBody request: ScoreRequest,
        @AuthenticationPrincipal principal: ApiKeyPrincipal?
    ): ResponseEntity<ScoreResponse> {
        val userId = userIdOrUnauthorized(principal) ?: return ResponseEntity.status(401).build()
        val code = parseExamCode(examCode) ?: return ResponseEntity.badRequest().build()
        return try {
            val score = coachService.scorePracticeExam(code, request.answers)
            if (request.save == true) {
                coachService.savePracticeExamResult(userId, code, score)
            }
            ResponseEntity.ok(score)
        } catch (e: DataAccessException) {
            ResponseEntity.internalServerError().build()
        }
    }

    private fun parseExamCode(s: String): ExamCode? = when (s.uppercase()) {
        "SIE" -> ExamCode.SIE
        "SERIES_7", "SERIES7" -> ExamCode.SERIES_7
        "SERIES_57", "SERIES57" -> ExamCode.SERIES_57
        "SERIES_65", "SERIES65" -> ExamCode.SERIES_65
        else -> null
    }

    private fun parseChoiceLetter(s: String): ChoiceLetter? = when (s.uppercase()) {
        "A" -> ChoiceLetter.A
        "B" -> ChoiceLetter.B
        "C" -> ChoiceLetter.C
        "D" -> ChoiceLetter.D
        else -> null
    }

    data class RecordAnswerResponse(val correct: Boolean)
}
