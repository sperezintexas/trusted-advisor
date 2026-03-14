package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.Persona
import com.atxbogart.trustedadvisor.model.ExamCode
import com.atxbogart.trustedadvisor.repository.PersonaRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class PersonaService(private val repository: PersonaRepository) {

    fun findAll() = repository.findAll()

    fun findById(id: String) = repository.findById(id).orElse(null)

    fun findCoachPersonaForExam(examCode: ExamCode): Persona? {
        val preferredId = examCoachPersonaId(examCode)
        return findById(preferredId)
            ?: findByName(examCoachPersonaName(examCode))
            ?: findDefaultCoachPersona()
    }

    private fun findByName(name: String): Persona? = repository.findByNameIgnoreCase(name)

    /**
     * Default persona for coach/exam question workflows.
     * Prefer explicit finance-coach id/name, then finance expert fallback.
     */
    fun findDefaultCoachPersona(): Persona? {
        val personas = repository.findAll()
        return personas.firstOrNull { it.id.equals("finance-coach", ignoreCase = true) }
            ?: personas.firstOrNull { it.name.equals("finance-coach", ignoreCase = true) }
            ?: personas.firstOrNull { it.name.equals("finance coach", ignoreCase = true) }
            ?: personas.firstOrNull { it.name.equals("finance expert", ignoreCase = true) }
            ?: personas.firstOrNull {
                val n = it.name.lowercase()
                n.contains("finance") && n.contains("coach")
            }
    }

    fun detectExamCodeFromMessage(message: String): ExamCode? {
        val normalized = message.lowercase()
        return when {
            normalized.contains("series 65") || normalized.contains("series_65") || normalized.contains("series65") ->
                ExamCode.SERIES_65
            normalized.contains("series 57") || normalized.contains("series_57") || normalized.contains("series57") ->
                ExamCode.SERIES_57
            normalized.contains("series 7") || normalized.contains("series_7") || normalized.contains("series7") ->
                ExamCode.SERIES_7
            Regex("\\bsie\\b").containsMatchIn(normalized) ->
                ExamCode.SIE
            else -> null
        }
    }

    fun save(persona: Persona): Persona = 
        repository.save(persona.copy(updatedAt = LocalDateTime.now(ZoneOffset.UTC)))

    fun deleteById(id: String) = repository.deleteById(id)

    @PostConstruct
    private fun seedPredefined() {
        try {
            ensurePersona(
                id = "finance-coach",
                name = "Finance Coach",
                description = "Investment and options trading advisor",
                systemPrompt = "You are a senior finance expert specializing in stock/options strategies, risk management, and market analysis. Be direct, data-driven. Use enabled tools for live data when available.",
                webSearchEnabled = true,
                yahooFinanceEnabled = true
            )
            ensurePersona(
                id = null,
                name = "Trusted Advisor",
                description = "Multi-domain expert: finance, medical, legal, tax",
                systemPrompt = "You are Dr. Elias Voss, trusted multi-expert across finance, medicine, law, tax. Prioritize protection/optimization. Structure: bottom-line first, red flags bold, domain breakdown, next steps. Use enabled tools for live data when relevant. Disclaimer: general info only.",
                webSearchEnabled = true,
                yahooFinanceEnabled = true
            )
            ExamCode.entries.forEach { code ->
                ensurePersona(
                    id = examCoachPersonaId(code),
                    name = examCoachPersonaName(code),
                    description = "Dedicated ${code.name} exam coach persona with RAG PDF context.",
                    systemPrompt = examCoachSystemPrompt(code),
                    webSearchEnabled = false,
                    yahooFinanceEnabled = false
                )
            }
        } catch (e: Exception) {
            // Mongo unreachable at startup (e.g. not running); skip seed. Log and continue.
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("Could not seed personas (Mongo may be down): {}", e.message)
        }
    }

    private fun ensurePersona(
        id: String?,
        name: String,
        description: String,
        systemPrompt: String,
        webSearchEnabled: Boolean,
        yahooFinanceEnabled: Boolean
    ) {
        val existing = if (!id.isNullOrBlank()) {
            findById(id) ?: findByName(name)
        } else {
            findByName(name)
        }
        if (existing != null) return
        repository.save(
            Persona(
                id = id,
                name = name,
                description = description,
                systemPrompt = systemPrompt,
                webSearchEnabled = webSearchEnabled,
                yahooFinanceEnabled = yahooFinanceEnabled
            )
        )
    }

    private fun examCoachPersonaId(examCode: ExamCode): String = when (examCode) {
        ExamCode.SIE -> "coach-sie"
        ExamCode.SERIES_7 -> "coach-series-7"
        ExamCode.SERIES_57 -> "coach-series-57"
        ExamCode.SERIES_65 -> "coach-series-65"
    }

    private fun examCoachPersonaName(examCode: ExamCode): String = when (examCode) {
        ExamCode.SIE -> "Coach SIE"
        ExamCode.SERIES_7 -> "Coach Series 7"
        ExamCode.SERIES_57 -> "Coach Series 57"
        ExamCode.SERIES_65 -> "Coach Series 65"
    }

    private fun examCoachSystemPrompt(examCode: ExamCode): String {
        val label = when (examCode) {
            ExamCode.SIE -> "SIE"
            ExamCode.SERIES_7 -> "Series 7"
            ExamCode.SERIES_57 -> "Series 57"
            ExamCode.SERIES_65 -> "Series 65"
        }
        return "You are a dedicated $label exam coach. Use only persona RAG context from attached study PDFs and user-provided facts. Focus on exam-style explanations, topic remediation, and concise learning plans. If context is missing, say what document/topic is needed."
    }
}