package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.config.ExamTopicWeights
import com.atxbogart.trustedadvisor.model.ChoiceLetter
import com.atxbogart.trustedadvisor.model.ChatMessage
import com.atxbogart.trustedadvisor.model.CoachChoice
import com.atxbogart.trustedadvisor.model.CoachQuestion
import com.atxbogart.trustedadvisor.model.ExamCode
import com.atxbogart.trustedadvisor.model.GrokRequest
import com.atxbogart.trustedadvisor.repository.CoachQuestionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.time.ZoneOffset

data class GeneratedQuestionView(
    val question: String,
    val choices: List<GeneratedChoiceView>,
    val correctLetter: String,
    val explanation: String,
    val topic: String? = null
)

data class GeneratedChoiceView(
    val letter: String,
    val text: String
)

sealed class GenerateQuestionsResult {
    data class Success(val questions: List<GeneratedQuestionView>) : GenerateQuestionsResult()
    data class NoContext(val message: String) : GenerateQuestionsResult()
    data class Error(val message: String) : GenerateQuestionsResult()
}

@Service
class QuestionGeneratorService(
    private val personaFileService: PersonaFileService,
    private val grokService: GrokService,
    private val coachQuestionRepository: CoachQuestionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    companion object {
        private const val FILE_CONTEXT_MAX_TOKENS = 12_000
    }

    fun generateQuestions(
        personaId: String,
        count: Int,
        examCode: ExamCode? = null,
        saveToPool: Boolean = false
    ): GenerateQuestionsResult {
        val context = personaFileService.getFileContext(personaId, FILE_CONTEXT_MAX_TOKENS)
        if (context.isBlank()) {
            return GenerateQuestionsResult.NoContext(
                "No indexed documents for this persona. Upload and index documents in the RAG Documents tab first."
            )
        }

        val requestedCount = count.coerceIn(1, 25)
        val topicList = examCode?.let { ExamTopicWeights.topicNamesForExam(it) } ?: emptyList()
        val topicInstruction = if (topicList.isNotEmpty()) {
            "- Assign each question exactly one topic from this list (use the string exactly): ${topicList.joinToString(", ")}. Include \"topic\" (string) in each element."
        } else {
            "- Optionally include \"topic\" (string) for each question if the material suggests a category."
        }

        val systemPrompt = """You are an expert finance exam question writer. Your task is to generate multiple-choice practice questions based ONLY on the provided reference material.

Rules:
- Generate exactly $requestedCount questions.
- Each question must be answerable from the reference material only. Do not use outside knowledge.
- Output a valid JSON array only, no other text or markdown. Each element must have: "question" (string), "choices" (array of {"letter": "A"|"B"|"C"|"D", "text": string}), "correctLetter" (string "A"|"B"|"C"|"D"), "explanation" (string, 1-3 sentences).
$topicInstruction
- Use clear, professional wording."""

        val userContent = """Reference material (use this as the sole source for your questions):

---
$context
---

Generate exactly $requestedCount multiple-choice questions as a JSON array. Output only the JSON array, no code fence or explanation before or after."""

        return try {
            val request = GrokRequest(
                messages = listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", userContent)
                ),
                temperature = 0.5,
                max_tokens = 4096
            )
            val response = grokService.chat(request)
            val content = response.choices.getOrNull(0)?.message?.content?.trim()
                ?: return GenerateQuestionsResult.Error("Empty response from model")

            val questions = parseQuestionsFromResponse(content, topicList)
            if (questions.isEmpty()) {
                GenerateQuestionsResult.Error("Model did not return valid questions. Raw response: ${content.take(500)}")
            } else {
                if (saveToPool && examCode != null && questions.isNotEmpty()) {
                    saveToPool(examCode, questions)
                }
                log.info("[question-generator] Generated {} questions for persona {} (examCode={}, savedToPool={})", questions.size, personaId, examCode, saveToPool)
                GenerateQuestionsResult.Success(questions)
            }
        } catch (e: Exception) {
            log.warn("[question-generator] Failed: {}", e.message)
            GenerateQuestionsResult.Error(e.message ?: "Generation failed")
        }
    }

    private fun saveToPool(examCode: ExamCode, questions: List<GeneratedQuestionView>) {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val validTopics = ExamTopicWeights.topicNamesForExam(examCode).toSet()
        for (q in questions) {
            val topic = q.topic?.takeIf { it in validTopics } ?: q.topic
            val choices = q.choices.map { c ->
                CoachChoice(letter = ChoiceLetter.valueOf(c.letter), text = c.text)
            }
            val correctLetter = when (q.correctLetter.uppercase()) {
                "A" -> ChoiceLetter.A
                "B" -> ChoiceLetter.B
                "C" -> ChoiceLetter.C
                "D" -> ChoiceLetter.D
                else -> ChoiceLetter.A
            }
            val coachQuestion = CoachQuestion(
                examCode = examCode,
                question = q.question,
                choices = choices,
                correctLetter = correctLetter,
                explanation = q.explanation,
                topic = topic,
                source = "generated",
                active = true,
                createdAt = now,
                updatedAt = now
            )
            coachQuestionRepository.save(coachQuestion)
        }
        log.info("[question-generator] Saved {} questions to pool for exam {}", questions.size, examCode)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseQuestionsFromResponse(content: String, validTopics: List<String> = emptyList()): List<GeneratedQuestionView> {
        val jsonStr = content
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return try {
            val raw = objectMapper.readValue<List<Map<String, Any?>>>(jsonStr)
            raw.mapNotNull { map ->
                try {
                    val question = map["question"] as? String ?: return@mapNotNull null
                    val choicesList = map["choices"] as? List<*> ?: return@mapNotNull null
                    val choiceLetters = listOf("A", "B", "C", "D")
                    val choices = choicesList.mapIndexed { index, c ->
                        val m = c as? Map<*, *> ?: return@mapNotNull null
                        val letter = (m["letter"]?.toString()?.uppercase()?.take(1))?.takeIf { it in choiceLetters }
                            ?: choiceLetters.getOrNull(index) ?: return@mapNotNull null
                        GeneratedChoiceView(
                            letter = letter,
                            text = m["text"]?.toString() ?: return@mapNotNull null
                        )
                    }
                    if (choices.size != 4) return@mapNotNull null
                    val correctLetter = (map["correctLetter"]?.toString() ?: "").uppercase().take(1)
                    if (correctLetter !in listOf("A", "B", "C", "D")) return@mapNotNull null
                    val explanation = map["explanation"]?.toString() ?: ""
                    val topic = map["topic"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                        ?.takeIf { validTopics.isEmpty() || it in validTopics }
                    GeneratedQuestionView(
                        question = question,
                        choices = choices,
                        correctLetter = correctLetter,
                        explanation = explanation,
                        topic = topic
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            log.debug("[question-generator] Parse failed: {}", e.message)
            emptyList()
        }
    }
}
