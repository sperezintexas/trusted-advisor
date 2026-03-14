package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.ChatMessage
import com.atxbogart.trustedadvisor.model.ExamCode
import com.atxbogart.trustedadvisor.model.LearningPlanRecommendation
import com.atxbogart.trustedadvisor.model.ToolExecutionPolicy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CoachLearningPlanService(
    private val personaService: PersonaService,
    private val agentOrchestratorService: AgentOrchestratorService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun buildRecommendation(
        examCode: ExamCode,
        correct: Int,
        total: Int,
        percentage: Double,
        passingPercentage: Int,
        missedTopics: List<String>
    ): LearningPlanRecommendation {
        val defaultCoachPersonaId = personaService.findDefaultCoachPersona()?.id ?: "finance-coach"
        val prompt = """
            Student just completed a ${examCode.name} practice exam.
            Result: $correct / $total correct (${String.format("%.1f", percentage)}%).
            Passing score: $passingPercentage%.
            Missed topics: ${if (missedTopics.isEmpty()) "General review" else missedTopics.joinToString(", ")}.
            
            Provide a concise learning plan with:
            1) one short summary paragraph (2-4 sentences),
            2) suggestedTopics (3-5),
            3) proposedLearningPlan steps (4-6 short bullets, imperative voice).
            
            Format strictly as JSON:
            {
              "summary": "...",
              "suggestedTopics": ["..."],
              "proposedLearningPlan": ["..."]
            }
        """.trimIndent()

        return try {
            val orchestrated = agentOrchestratorService.orchestrate(
                AgentOrchestratorRequest(
                    userMessage = prompt,
                    personaId = defaultCoachPersonaId,
                    systemPrompt = "You are a FINRA coaching specialist. Give practical, exam-focused guidance.",
                    history = listOf(ChatMessage("system", "Return only valid JSON.")),
                    maxOutputTokens = 700,
                    toolPolicy = ToolExecutionPolicy(
                        webSearchEnabled = false,
                        yahooFinanceEnabled = false,
                        internalActionsEnabled = true
                    )
                )
            )
            parseRecommendation(orchestrated.response) ?: fallbackRecommendation(
                examCode = examCode,
                passingPercentage = passingPercentage,
                percentage = percentage,
                missedTopics = missedTopics
            )
        } catch (e: Exception) {
            log.warn("[coach-plan] Could not generate AI recommendation: {}", e.message)
            fallbackRecommendation(examCode, passingPercentage, percentage, missedTopics)
        }
    }

    private fun parseRecommendation(raw: String): LearningPlanRecommendation? {
        val cleaned = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val summary = Regex("\"summary\"\\s*:\\s*\"([\\s\\S]*?)\"")
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\n", "\n")
            ?.replace("\\\"", "\"")
            ?.trim()
            ?: return null

        val topicsRaw = Regex("\"suggestedTopics\"\\s*:\\s*\\[([\\s\\S]*?)\\]")
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        val stepsRaw = Regex("\"proposedLearningPlan\"\\s*:\\s*\\[([\\s\\S]*?)\\]")
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()

        val suggestedTopics = Regex("\"([^\"]+)\"").findAll(topicsRaw).map { it.groupValues[1].trim() }.toList()
        val proposedLearningPlan = Regex("\"([^\"]+)\"").findAll(stepsRaw).map { it.groupValues[1].trim() }.toList()

        return LearningPlanRecommendation(
            summary = summary,
            suggestedTopics = suggestedTopics.take(5),
            proposedLearningPlan = proposedLearningPlan.take(6)
        )
    }

    private fun fallbackRecommendation(
        examCode: ExamCode,
        passingPercentage: Int,
        percentage: Double,
        missedTopics: List<String>
    ): LearningPlanRecommendation {
        val topTopics = missedTopics.take(5).ifEmpty { listOf("Core concepts review", "Question strategy") }
        val gap = (passingPercentage - percentage).coerceAtLeast(0.0)
        val passed = percentage >= passingPercentage
        val summary = if (passed) {
            "You are above the passing mark for ${examCode.name}. Keep momentum by reinforcing weak topics and maintaining timed mixed sets to preserve pace and confidence."
        } else {
            "You are ${String.format("%.1f", gap)} points below the passing mark for ${examCode.name}. Focus on weak-topic remediation first, then timed mixed sets to rebuild exam pace and confidence."
        }
        return LearningPlanRecommendation(
            summary = summary,
            suggestedTopics = topTopics,
            proposedLearningPlan = listOf(
                "Review notes and explanations for the top missed topics today.",
                "Do 20 untimed questions focused on those topics and write down why each wrong answer was wrong.",
                "Create a one-page cheat sheet of formulas/rules and review it before each session.",
                "Run a timed 20-question mixed set every day for the next 3 days.",
                "Retake a full practice exam and compare topic-level accuracy to this attempt."
            )
        )
    }
}
