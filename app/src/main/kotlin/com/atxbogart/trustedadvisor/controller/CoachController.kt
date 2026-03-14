package com.atxbogart.trustedadvisor.controller

import com.atxbogart.trustedadvisor.config.ApiKeyPrincipal
import com.atxbogart.trustedadvisor.model.CheckAnswerResult
import com.atxbogart.trustedadvisor.model.ChoiceLetter
import com.atxbogart.trustedadvisor.model.CoachAnswerRequest
import com.atxbogart.trustedadvisor.model.CoachExam
import com.atxbogart.trustedadvisor.model.CoachExamAttempt
import com.atxbogart.trustedadvisor.model.CoachQuestion
import com.atxbogart.trustedadvisor.model.CoachUserProgress
import com.atxbogart.trustedadvisor.model.ExamCode
import com.atxbogart.trustedadvisor.model.PracticeSessionResponse
import com.atxbogart.trustedadvisor.model.RecommendationStatus
import com.atxbogart.trustedadvisor.model.ScoreRequest
import com.atxbogart.trustedadvisor.model.ScoreResponse
import com.atxbogart.trustedadvisor.service.CoachService
import com.atxbogart.trustedadvisor.service.UsageLimitService
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/coach")
@CrossOrigin(origins = ["http://localhost:3000"])
class CoachController(
    private val coachService: CoachService,
    private val usageLimitService: UsageLimitService,
    @Value("\${app.skip-auth:false}") private val skipAuth: Boolean
) {

    private fun userIdOrUnauthorized(principal: ApiKeyPrincipal?): String? =
        principal?.userId
            ?: currentEmailFromOAuth2()
            ?: if (skipAuth) "dev-user" else null

    @GetMapping("/exams")
    fun getExams(): ResponseEntity<List<CoachExam>> =
        try {
            ResponseEntity.ok(coachService.getExams())
        } catch (e: DataAccessException) {
            ResponseEntity.ok(emptyList())
        }

    @GetMapping("/exams/{examCode}/pool-size")
    fun getPoolSize(
        @PathVariable examCode: String
    ): ResponseEntity<PoolSizeResponse> {
        val code = parseExamCode(examCode) ?: return ResponseEntity.badRequest().build()
        return try {
            val count = coachService.getPoolSize(code)
            ResponseEntity.ok(PoolSizeResponse(examCode = examCode, count = count))
        } catch (e: DataAccessException) {
            ResponseEntity.ok(PoolSizeResponse(examCode = examCode, count = 0))
        }
    }

    @GetMapping("/questions/{examCode}")
    fun getRandomQuestion(
        @PathVariable examCode: String,
        @RequestParam(required = false) excludeIds: List<String>? = null,
        @AuthenticationPrincipal principal: ApiKeyPrincipal?
    ): ResponseEntity<CoachQuestion?> {
        val userId = userIdOrUnauthorized(principal) ?: return ResponseEntity.status(401).build()
        val code = parseExamCode(examCode) ?: return ResponseEntity.badRequest().build()
        if (!skipAuth && usageLimitService.coachUsageStatus(userId).isAtLimit) {
            return ResponseEntity.status(403).build()
        }
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
        if (parseExamCode(examCode) == null) return ResponseEntity.badRequest().build()
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
        @RequestParam count: Int = 75,
        @AuthenticationPrincipal principal: ApiKeyPrincipal?
    ): ResponseEntity<PracticeSessionResponse> {
        val userId = userIdOrUnauthorized(principal) ?: return ResponseEntity.status(401).build()
        val code = parseExamCode(examCode) ?: return ResponseEntity.badRequest().build()
        return try {
            val requestedCount = count.coerceIn(1, 200)
            if (!skipAuth) {
                val usage = usageLimitService.coachUsageStatus(userId)
                if (usage.isAtLimit) {
                    return ResponseEntity.status(403).build()
                }
                val remaining = usage.remaining
                val effectiveCount = if (remaining != null) requestedCount.coerceAtMost(remaining) else requestedCount
                val session = coachService.getPracticeExamQuestions(code, effectiveCount)
                return ResponseEntity.ok(session)
            }
            val session = coachService.getPracticeExamQuestions(code, requestedCount)
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
            val evaluation = coachService.scorePracticeExamDetailed(code, request.answers)
            val recommendationStatus = if (request.save == true) RecommendationStatus.QUEUED else null
            val score = evaluation.score.copy(
                recommendation = null,
                recommendationStatus = recommendationStatus,
                recommendationJobSubmitted = request.save == true
            )
            if (request.save == true) {
                coachService.savePracticeExamResult(
                    userId = userId,
                    examCode = code,
                    score = score,
                    missedTopics = evaluation.missedTopics,
                    recommendationStatus = RecommendationStatus.QUEUED
                )
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
    data class PoolSizeResponse(val examCode: String, val count: Long)

    private fun currentEmailFromOAuth2(): String? {
        val auth = SecurityContextHolder.getContext().authentication as? OAuth2AuthenticationToken ?: return null
        val principal = auth.principal
        val attrs = principal.attributes
        return (attrs["email"] as? String)
            ?: (attrs["login"] as? String)?.let { "$it@github.local" }
            ?: (attrs["preferred_username"] as? String)?.let { "$it@x.local" }
            ?: (attrs["username"] as? String)?.let { "$it@x.local" }
            ?: (attrs["sub"] as? String)?.let { "x-$it@x.local" }
    }
}
