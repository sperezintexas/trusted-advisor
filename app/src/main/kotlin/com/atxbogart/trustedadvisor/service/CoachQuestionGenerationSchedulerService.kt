package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.CoachQuestionGenerationConfig
import com.atxbogart.trustedadvisor.model.ExamCode
import com.atxbogart.trustedadvisor.repository.CoachQuestionGenerationConfigRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset

data class CoachGenerationConfigView(
    val examCode: ExamCode,
    val enabled: Boolean,
    val personaId: String,
    val targetPoolSize: Int,
    val intervalMinutes: Int,
    val chunkSize: Int,
    val currentPoolSize: Long,
    val nextRunAt: String,
    val lastRunAt: String?,
    val running: Boolean,
    val lastStatus: String?,
    val lastMessage: String?
)

data class CoachGenerationConfigUpdate(
    val enabled: Boolean? = null,
    val personaId: String? = null,
    val targetPoolSize: Int? = null,
    val intervalMinutes: Int? = null
)

@Service
class CoachQuestionGenerationSchedulerService(
    private val configRepository: CoachQuestionGenerationConfigRepository,
    private val coachService: CoachService,
    private val questionGeneratorService: QuestionGeneratorService,
    private val personaService: PersonaService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val chunkSize = 25

    @PostConstruct
    fun seedConfigs() {
        coachService.getExams().forEach { exam ->
            val configId = configId(exam.code)
            if (!configRepository.existsById(configId)) {
                configRepository.save(
                    CoachQuestionGenerationConfig(
                        id = configId,
                        examCode = exam.code,
                        enabled = false,
                        personaId = personaService.findDefaultCoachPersona()?.id ?: "finance-coach",
                        targetPoolSize = exam.totalQuestionsInOutline,
                        intervalMinutes = 60,
                        chunkSize = chunkSize,
                        nextRunAt = LocalDateTime.now(ZoneOffset.UTC)
                    )
                )
            }
        }
    }

    fun getConfigs(): List<CoachGenerationConfigView> {
        val byExam = configRepository.findAll().associateBy { it.examCode }
        return coachService.getExams().map { exam ->
            val cfg = byExam[exam.code] ?: defaultConfigForExam(exam.code, exam.totalQuestionsInOutline)
            val pool = coachService.getPoolSize(exam.code)
            cfg.toView(pool)
        }.sortedBy { it.examCode.name }
    }

    fun updateConfig(examCode: ExamCode, update: CoachGenerationConfigUpdate): CoachGenerationConfigView {
        val current = configRepository.findByExamCode(examCode)
            ?: defaultConfigForExam(examCode, defaultTargetForExam(examCode))
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val merged = current.copy(
            enabled = update.enabled ?: current.enabled,
            personaId = update.personaId?.takeIf { it.isNotBlank() } ?: current.personaId,
            targetPoolSize = (update.targetPoolSize ?: current.targetPoolSize).coerceIn(25, 5000),
            intervalMinutes = (update.intervalMinutes ?: current.intervalMinutes).coerceIn(1, 1440),
            chunkSize = chunkSize,
            nextRunAt = if ((update.enabled ?: current.enabled)) now else current.nextRunAt,
            updatedAt = now
        )
        val saved = configRepository.save(merged)
        return saved.toView(coachService.getPoolSize(examCode))
    }

    fun runNow(examCode: ExamCode): CoachGenerationConfigView {
        val current = configRepository.findByExamCode(examCode)
            ?: defaultConfigForExam(examCode, defaultTargetForExam(examCode))
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val updated = configRepository.save(
            current.copy(
                enabled = true,
                nextRunAt = now,
                updatedAt = now
            )
        )
        return updated.toView(coachService.getPoolSize(examCode))
    }

    @Scheduled(fixedDelay = 5000L)
    fun tick() {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val configs = configRepository.findAll()
            .filter { it.enabled && !it.running && !it.nextRunAt.isAfter(now) }
            .sortedBy { it.nextRunAt }
            .take(4)

        configs.forEach { cfg ->
            processConfig(cfg)
        }
    }

    private fun processConfig(config: CoachQuestionGenerationConfig) {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        var state = configRepository.save(
            config.copy(
                running = true,
                lastRunAt = now,
                lastStatus = "RUNNING",
                lastMessage = "Generating next chunk...",
                updatedAt = now
            )
        )
        try {
            val currentPool = coachService.getPoolSize(config.examCode)
            val remaining = (config.targetPoolSize - currentPool).toInt()
            if (remaining <= 0) {
                configRepository.save(
                    state.copy(
                        running = false,
                        lastStatus = "UP_TO_DATE",
                        lastMessage = "Pool target already met ($currentPool/${config.targetPoolSize}).",
                        nextRunAt = now.plusMinutes(config.intervalMinutes.toLong()),
                        updatedAt = LocalDateTime.now(ZoneOffset.UTC)
                    )
                )
                return
            }

            val count = minOf(chunkSize, remaining)
            val result = questionGeneratorService.generateQuestions(
                personaId = config.personaId,
                count = count,
                examCode = config.examCode,
                saveToPool = true
            )
            val postPool = coachService.getPoolSize(config.examCode)
            state = when (result) {
                is GenerateQuestionsResult.Success -> {
                    val stillRemaining = config.targetPoolSize - postPool
                    val nextRun = if (stillRemaining > 0) {
                        now.plusSeconds(5)
                    } else {
                        now.plusMinutes(config.intervalMinutes.toLong())
                    }
                    state.copy(
                        running = false,
                        lastStatus = "SUCCESS",
                        lastMessage = "Generated ${result.questions.size} questions. Pool now $postPool/${config.targetPoolSize}.",
                        nextRunAt = nextRun,
                        updatedAt = LocalDateTime.now(ZoneOffset.UTC)
                    )
                }
                is GenerateQuestionsResult.NoContext -> {
                    state.copy(
                        running = false,
                        lastStatus = "NO_CONTEXT",
                        lastMessage = result.message,
                        nextRunAt = now.plusMinutes(config.intervalMinutes.toLong()),
                        updatedAt = LocalDateTime.now(ZoneOffset.UTC)
                    )
                }
                is GenerateQuestionsResult.Error -> {
                    state.copy(
                        running = false,
                        lastStatus = "ERROR",
                        lastMessage = result.message,
                        nextRunAt = now.plusMinutes(config.intervalMinutes.toLong()),
                        updatedAt = LocalDateTime.now(ZoneOffset.UTC)
                    )
                }
            }
            configRepository.save(state)
        } catch (e: Exception) {
            log.warn("[coach-gen] {} failed: {}", config.examCode, e.message)
            configRepository.save(
                state.copy(
                    running = false,
                    lastStatus = "ERROR",
                    lastMessage = e.message ?: "Unknown error",
                    nextRunAt = now.plusMinutes(config.intervalMinutes.toLong()),
                    updatedAt = LocalDateTime.now(ZoneOffset.UTC)
                )
            )
        }
    }

    private fun defaultTargetForExam(examCode: ExamCode): Int {
        return coachService.getExams().firstOrNull { it.code == examCode }?.totalQuestionsInOutline ?: 100
    }

    private fun defaultConfigForExam(examCode: ExamCode, target: Int): CoachQuestionGenerationConfig {
        val cfg = CoachQuestionGenerationConfig(
            id = configId(examCode),
            examCode = examCode,
            enabled = false,
            personaId = personaService.findDefaultCoachPersona()?.id ?: "finance-coach",
            targetPoolSize = target,
            intervalMinutes = 60,
            chunkSize = chunkSize,
            nextRunAt = LocalDateTime.now(ZoneOffset.UTC)
        )
        return configRepository.save(cfg)
    }

    private fun configId(examCode: ExamCode): String = "coach-gen-${examCode.name}"

    private fun CoachQuestionGenerationConfig.toView(pool: Long): CoachGenerationConfigView {
        return CoachGenerationConfigView(
            examCode = examCode,
            enabled = enabled,
            personaId = personaId,
            targetPoolSize = targetPoolSize,
            intervalMinutes = intervalMinutes,
            chunkSize = chunkSize,
            currentPoolSize = pool,
            nextRunAt = nextRunAt.toString(),
            lastRunAt = lastRunAt?.toString(),
            running = running,
            lastStatus = lastStatus,
            lastMessage = lastMessage
        )
    }
}
