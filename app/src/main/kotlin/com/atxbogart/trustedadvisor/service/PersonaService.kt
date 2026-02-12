package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.Persona
import com.atxbogart.trustedadvisor.repository.PersonaRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class PersonaService(private val repository: PersonaRepository) {

    fun findAll() = repository.findAll()

    fun findById(id: String) = repository.findById(id).orElse(null)

    fun save(persona: Persona): Persona = 
        repository.save(persona.copy(updatedAt = LocalDateTime.now(ZoneOffset.UTC)))

    fun deleteById(id: String) = repository.deleteById(id)

    @PostConstruct
    private fun seedPredefined() {
        try {
            if (repository.count() > 0L) return
            val predefined = listOf(
                Persona(
                    name = "Finance Expert",
                    description = "Investment and options trading advisor",
                    systemPrompt = "You are a senior finance expert specializing in stock/options strategies, risk management, and market analysis. Be direct, data-driven, use provided context for prices/portfolio."
                ),
                Persona(
                    name = "Trusted Advisor",
                    description = "Multi-domain expert: finance, medical, legal, tax",
                    systemPrompt = "You are Dr. Elias Voss, trusted multi-expert across finance, medicine, law, tax. Prioritize protection/optimization. Structure: bottom-line first, red flags bold, domain breakdown, next steps. Disclaimer: general info only."
                )
            )
            predefined.forEach { repository.save(it) }
        } catch (e: Exception) {
            // Mongo unreachable at startup (e.g. not running); skip seed. Log and continue.
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("Could not seed personas (Mongo may be down): {}", e.message)
        }
    }
}