package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

enum class AgentRoute {
    RAG_ONLY,
    LIVE_RESEARCH,
    HYBRID,
    COACH_MODE
}

data class AgentOrchestratorRequest(
    val userMessage: String,
    val personaId: String?,
    val systemPrompt: String,
    val history: List<ChatMessage>,
    val maxOutputTokens: Int,
    val toolPolicy: ToolExecutionPolicy
)

data class AgentOrchestratorResult(
    val response: String,
    val usage: GrokUsage? = null,
    val citations: List<ChatCitation> = emptyList(),
    val toolEvents: List<ChatToolEvent> = emptyList()
)

@Service
class AgentOrchestratorService(
    private val personaFileService: PersonaFileService,
    private val grokService: GrokService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val webResearchTtlSeconds = 300L
    private val webCache = ConcurrentHashMap<String, CachedWebResearch>()

    fun orchestrate(request: AgentOrchestratorRequest): AgentOrchestratorResult {
        val toolEvents = mutableListOf<ChatToolEvent>()
        val route = routerAgent(request.userMessage, request.personaId)
        toolEvents.add(
            ChatToolEvent(
                type = "router",
                name = "router_agent",
                status = "selected",
                output = """{"route":"${route.name}"}"""
            )
        )

        val ragBundle = if (route == AgentRoute.RAG_ONLY || route == AgentRoute.HYBRID || route == AgentRoute.COACH_MODE) {
            ragAgent(
                personaId = request.personaId,
                query = request.userMessage
            )
        } else {
            RagEvidenceBundle()
        }
        if (ragBundle.chunks.isNotEmpty()) {
            toolEvents.add(
                ChatToolEvent(
                    type = "rag_retrieval",
                    name = "rag_agent",
                    status = "completed",
                    output = """{"chunks":${ragBundle.chunks.size},"tokens":${ragBundle.totalTokens}}"""
                )
            )
        }

        val liveBundle = if (request.toolPolicy.webSearchEnabled &&
            (route == AgentRoute.LIVE_RESEARCH || route == AgentRoute.HYBRID)
        ) {
            liveResearchAgent(request.userMessage)
        } else {
            LiveResearchBundle()
        }
        toolEvents.addAll(liveBundle.toolEvents)

        val synthesis = synthesisVerifierAgent(
            request = request,
            route = route,
            ragBundle = ragBundle,
            liveBundle = liveBundle
        )
        toolEvents.add(
            ChatToolEvent(
                type = "verification",
                name = "synthesis_verifier_agent",
                status = "completed",
                output = """{"confidence":"${synthesis.confidence}","route":"${route.name}"}"""
            )
        )

        val mergedCitations = linkedMapOf<String, ChatCitation>()
        (ragBundle.citations + liveBundle.citations + synthesis.citations).forEach { citation ->
            mergedCitations[citation.url] = citation
        }

        return AgentOrchestratorResult(
            response = synthesis.answer,
            usage = synthesis.usage,
            citations = mergedCitations.values.toList(),
            toolEvents = toolEvents
        )
    }

    private fun routerAgent(message: String, personaId: String?): AgentRoute {
        val normalized = message.lowercase()
        val hasCoachSignals = listOf(
            "sie", "series 7", "series 57", "series 65", "exam", "practice question", "finra", "nasaa"
        ).any { normalized.contains(it) }
        if (hasCoachSignals) return AgentRoute.COACH_MODE

        val hasLiveSignals = listOf(
            "today", "latest", "current", "now", "breaking", "price", "stock", "earnings", "news", "yield"
        ).any { normalized.contains(it) }
        val hasPersona = !personaId.isNullOrBlank()

        return when {
            hasLiveSignals && hasPersona -> AgentRoute.HYBRID
            hasLiveSignals -> AgentRoute.LIVE_RESEARCH
            hasPersona -> AgentRoute.RAG_ONLY
            else -> AgentRoute.RAG_ONLY
        }
    }

    private fun ragAgent(personaId: String?, query: String): RagEvidenceBundle {
        if (personaId.isNullOrBlank()) return RagEvidenceBundle()
        val chunks = personaFileService.getTopChunksForQuery(
            personaId = personaId,
            query = query,
            topK = 8,
            maxTokens = 3200
        )
        if (chunks.isEmpty()) return RagEvidenceBundle()
        val citations = chunks.map { chunk ->
            ChatCitation(
                url = "persona://${chunk.fileId}#chunk-${chunk.chunkIndex}",
                title = "${chunk.fileName} (chunk ${chunk.chunkIndex})",
                sourceType = "persona_chunk"
            )
        }
        return RagEvidenceBundle(
            chunks = chunks,
            citations = citations,
            totalTokens = chunks.sumOf { it.tokenCount }
        )
    }

    private fun liveResearchAgent(query: String): LiveResearchBundle {
        val key = query.trim().lowercase()
        cleanupExpiredCache()
        val cached = webCache[key]
        if (cached != null && cached.expiresAtEpochSeconds > Instant.now().epochSecond) {
            return cached.bundle.copy(
                toolEvents = listOf(
                    ChatToolEvent(
                        type = "web_research_cache",
                        name = "live_research_agent",
                        status = "hit",
                        output = """{"ttlSecondsRemaining":${cached.expiresAtEpochSeconds - Instant.now().epochSecond}}"""
                    )
                ) + cached.bundle.toolEvents
            )
        }

        val researchPrompt = listOf(
            ChatMessage(
                "system",
                "You are a research normalizer. Use web search to gather current facts and return concise bullet findings."
            ),
            ChatMessage(
                "user",
                "Research this query using current web data and provide 5-8 concise bullet findings: $query"
            )
        )
        val result = grokService.chatWithTools(
            request = GrokRequest(
                messages = researchPrompt,
                temperature = 0.2,
                max_tokens = 700
            ),
            toolPolicy = ToolExecutionPolicy(
                webSearchEnabled = true,
                yahooFinanceEnabled = false,
                internalActionsEnabled = false
            ),
            personaId = null
        )

        val normalizedSnippets = normalizeSnippets(result.response)
        val bundle = LiveResearchBundle(
            snippets = normalizedSnippets,
            citations = result.citations,
            toolEvents = listOf(
                ChatToolEvent(
                    type = "web_research_cache",
                    name = "live_research_agent",
                    status = "miss",
                    output = """{"cachedForSeconds":$webResearchTtlSeconds,"snippets":${normalizedSnippets.size}}"""
                )
            ) + result.toolEvents,
            usage = result.usage
        )
        webCache[key] = CachedWebResearch(
            expiresAtEpochSeconds = Instant.now().epochSecond + webResearchTtlSeconds,
            bundle = bundle.copy(toolEvents = emptyList())
        )
        return bundle
    }

    private fun synthesisVerifierAgent(
        request: AgentOrchestratorRequest,
        route: AgentRoute,
        ragBundle: RagEvidenceBundle,
        liveBundle: LiveResearchBundle
    ): SynthesisBundle {
        val ragEvidenceText = if (ragBundle.chunks.isEmpty()) {
            "(none)"
        } else {
            ragBundle.chunks.joinToString("\n\n") { chunk ->
                "[RAG:${chunk.fileName}#${chunk.chunkIndex}]\n${chunk.content.take(1600)}"
            }
        }
        val liveEvidenceText = if (liveBundle.snippets.isEmpty()) {
            "(none)"
        } else {
            liveBundle.snippets.joinToString("\n") { "- $it" }
        }
        val citeList = (ragBundle.citations + liveBundle.citations)
            .mapIndexed { index, c -> "[S${index + 1}] ${c.title ?: c.url} -> ${c.url}" }
            .joinToString("\n")
            .ifBlank { "(none)" }

        val verifierSystemPrompt = """
            You are the Synthesis/Verifier agent.
            Compose the final answer using only provided evidence. If evidence is missing, say what is unknown.
            Verify claims against evidence before stating them.
            Return strict JSON object with keys:
            - answer: string
            - confidence: one of HIGH, MEDIUM, LOW
            - sourceIndexes: array of integers that map to provided sources list
        """.trimIndent()

        val verifierUserPrompt = """
            Route: ${route.name}
            User question: ${request.userMessage}
            
            Sources:
            $citeList
            
            RAG evidence:
            $ragEvidenceText
            
            Live evidence:
            $liveEvidenceText
        """.trimIndent()

        val synthesisResponse = grokService.chat(
            GrokRequest(
                messages = request.history + listOf(
                    ChatMessage("system", request.systemPrompt),
                    ChatMessage("system", verifierSystemPrompt),
                    ChatMessage("user", verifierUserPrompt)
                ),
                temperature = 0.3,
                max_tokens = request.maxOutputTokens
            )
        )
        val raw = synthesisResponse.choices.getOrNull(0)?.message?.content.orEmpty().trim()
        val parsed = parseVerifierJson(raw)
        if (parsed == null) {
            log.warn("[orchestrator] Verifier JSON parse failed, returning raw answer")
            return SynthesisBundle(
                answer = raw,
                confidence = "LOW",
                citations = ragBundle.citations + liveBundle.citations,
                usage = synthesisResponse.usage
            )
        }

        val citations = (ragBundle.citations + liveBundle.citations).mapIndexedNotNull { index, c ->
            if ((index + 1) in parsed.sourceIndexes) c else null
        }.ifEmpty { ragBundle.citations + liveBundle.citations }

        return SynthesisBundle(
            answer = parsed.answer,
            confidence = parsed.confidence,
            citations = citations,
            usage = synthesisResponse.usage
        )
    }

    private fun normalizeSnippets(raw: String): List<String> {
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.removePrefix("- ").removePrefix("* ").trim() }
            .filter { it.isNotBlank() }
            .take(8)
    }

    private fun parseVerifierJson(raw: String): VerifierOutput? {
        val cleaned = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val answer = Regex("\"answer\"\\s*:\\s*\"([\\s\\S]*?)\"")
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\n", "\n")
            ?.replace("\\\"", "\"")
            ?.trim()
            ?: return null
        val confidence = Regex("\"confidence\"\\s*:\\s*\"(HIGH|MEDIUM|LOW)\"")
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            ?: "LOW"
        val sourceMatches = Regex("\\d+").findAll(
            Regex("\"sourceIndexes\"\\s*:\\s*\\[([^\\]]*)\\]")
                .find(cleaned)
                ?.groupValues
                ?.getOrNull(1)
                ?: ""
        )
        val indexes = sourceMatches.mapNotNull { it.value.toIntOrNull() }.toList()
        return VerifierOutput(answer = answer, confidence = confidence, sourceIndexes = indexes)
    }

    private fun cleanupExpiredCache() {
        val now = Instant.now().epochSecond
        webCache.entries.removeIf { it.value.expiresAtEpochSeconds <= now }
    }
}

private data class RagEvidenceBundle(
    val chunks: List<PersonaEvidenceChunk> = emptyList(),
    val citations: List<ChatCitation> = emptyList(),
    val totalTokens: Int = 0
)

private data class LiveResearchBundle(
    val snippets: List<String> = emptyList(),
    val citations: List<ChatCitation> = emptyList(),
    val toolEvents: List<ChatToolEvent> = emptyList(),
    val usage: GrokUsage? = null
)

private data class SynthesisBundle(
    val answer: String,
    val confidence: String,
    val citations: List<ChatCitation>,
    val usage: GrokUsage? = null
)

private data class CachedWebResearch(
    val expiresAtEpochSeconds: Long,
    val bundle: LiveResearchBundle
)

private data class VerifierOutput(
    val answer: String,
    val confidence: String,
    val sourceIndexes: List<Int>
)
