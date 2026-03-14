package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.*
import com.atxbogart.trustedadvisor.repository.ChatHistoryRepository
import com.atxbogart.trustedadvisor.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class ChatService(
    private val personaService: PersonaService,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val agentOrchestratorService: AgentOrchestratorService,
    private val chatConfigService: ChatConfigService,
    private val userRepository: UserRepository,
    private val planLimitService: PlanLimitService,
    private val tokenUsageService: TokenUsageService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendMessage(request: ChatRequest): ChatResponse {
        val role = resolveUserRole(request.userId)
        val limits = planLimitService.limitsForRole(role)
        val usedTodayBefore = tokenUsageService.getTodayUsage(request.userId)
        if (usedTodayBefore >= limits.dailyTokenLimit) {
            throw TokenLimitExceededException(
                role = role.name,
                used = usedTodayBefore,
                limit = limits.dailyTokenLimit
            )
        }

        val history = chatHistoryRepository.findByUserId(request.userId)?.messages ?: mutableListOf()
        val isCoachRequest = shouldUseDefaultCoachPersona(request.message)

        val requestedPersona = request.personaId?.let { personaService.findById(it) }
        val inferredExamCode = if (requestedPersona == null && isCoachRequest) {
            personaService.detectExamCodeFromMessage(request.message)
        } else {
            null
        }

        val defaultCoachPersona = if (requestedPersona == null && isCoachRequest) {
            inferredExamCode?.let { personaService.findCoachPersonaForExam(it) }
                ?: personaService.findDefaultCoachPersona()
        } else {
            null
        }
        val persona = requestedPersona ?: defaultCoachPersona
        val effectivePersonaId = request.personaId ?: defaultCoachPersona?.id
        val basePrompt = persona?.systemPrompt
            ?: "You are a helpful trusted advisor. Use provided context."

        val config = chatConfigService.getConfig()
        val baseToolPolicy = ToolExecutionPolicy(
            webSearchEnabled = (persona?.webSearchEnabled ?: true) && (config.tools["webSearch"] ?: true),
            yahooFinanceEnabled = (persona?.yahooFinanceEnabled ?: true) && (config.tools["yahooFinance"] ?: true),
            internalActionsEnabled = config.tools["internalActions"] ?: true
        )
        // Coach/exam flows rely on curated persona RAG content by default (no live web/finance tools).
        val toolPolicy = if (isCoachRequest) {
            baseToolPolicy.copy(
                webSearchEnabled = false,
                yahooFinanceEnabled = false
            )
        } else {
            baseToolPolicy
        }
        val toolsSuffix = buildToolsAwarenessSuffix(toolPolicy)

        val systemPrompt = buildSystemPrompt(basePrompt, toolsSuffix)

        val trimmedHistory = history.takeLast(limits.maxHistoryMessages)
        val messages = trimToPromptBudget(
            systemPrompt = systemPrompt,
            history = trimmedHistory,
            userMessage = request.message,
            maxPromptTokens = limits.maxPromptTokens
        )

        val orchestrated = agentOrchestratorService.orchestrate(
            AgentOrchestratorRequest(
                userMessage = request.message,
                personaId = effectivePersonaId,
                systemPrompt = systemPrompt,
                history = trimmedHistory,
                maxOutputTokens = limits.maxOutputTokens,
                toolPolicy = toolPolicy
            )
        )
        val responseContent = orchestrated.response

        val consumedTokens = orchestrated.usage?.total_tokens?.toLong()
            ?: estimateTokens(messages.sumOf { it.content.length } + responseContent.length).toLong()
        val usedTodayAfter = tokenUsageService.addTodayUsage(request.userId, consumedTokens)
        if (usedTodayAfter > limits.dailyTokenLimit) {
            log.info(
                "[chat] User {} exceeded daily tokens after request: {}/{} ({})",
                request.userId,
                usedTodayAfter,
                limits.dailyTokenLimit,
                role
            )
        }

        history.add(ChatMessage("user", request.message))
        history.add(ChatMessage("assistant", responseContent))

        val chatHistory = chatHistoryRepository.findByUserId(request.userId)
            ?: ChatHistory(userId = request.userId, messages = history.toMutableList())
        chatHistoryRepository.save(chatHistory.copy(updatedAt = LocalDateTime.now(ZoneOffset.UTC)))

        return ChatResponse(
            response = responseContent,
            usage = orchestrated.usage,
            citations = orchestrated.citations,
            toolEvents = orchestrated.toolEvents
        )
    }

    fun getChatLimits(userId: String): ChatLimitsView {
        val role = resolveUserRole(userId)
        val limits = planLimitService.limitsForRole(role)
        val usedToday = tokenUsageService.getTodayUsage(userId)
        val remaining = (limits.dailyTokenLimit - usedToday).coerceAtLeast(0L)
        return ChatLimitsView(
            role = role.name,
            maxOutputTokens = limits.maxOutputTokens,
            maxFileContextTokens = limits.maxFileContextTokens,
            maxHistoryMessages = limits.maxHistoryMessages,
            maxPromptTokens = limits.maxPromptTokens,
            dailyTokenLimit = limits.dailyTokenLimit,
            usedToday = usedToday,
            remainingToday = remaining
        )
    }

    fun getHistory(userId: String): List<ChatMessage> =
        chatHistoryRepository.findByUserId(userId)?.messages ?: emptyList()

    private fun resolveUserRole(userId: String): UserRole {
        if (userId == "dev-user") return UserRole.ADMIN
        return userRepository.findByEmail(userId)?.role ?: UserRole.BASIC
    }

    private fun trimToPromptBudget(
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String,
        maxPromptTokens: Int
    ): List<ChatMessage> {
        if (maxPromptTokens <= 0) {
            return listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", userMessage)
            )
        }
        val historyWindow = history.toMutableList()
        while (historyWindow.isNotEmpty()) {
            val candidate = listOf(ChatMessage("system", systemPrompt)) +
                historyWindow +
                ChatMessage("user", userMessage)
            if (estimateTokens(candidate.sumOf { it.content.length }) <= maxPromptTokens) {
                return candidate
            }
            historyWindow.removeAt(0)
        }
        return listOf(
            ChatMessage("system", systemPrompt),
            ChatMessage("user", userMessage)
        )
    }

    private fun estimateTokens(charCount: Int): Int = (charCount / 4.0).toInt()

    private fun buildSystemPrompt(basePrompt: String, toolsSuffix: String): String {
        val parts = mutableListOf(basePrompt)

        if (toolsSuffix.isNotBlank()) {
            parts.add(toolsSuffix)
        }

        return parts.joinToString("\n\n")
    }

    private fun shouldUseDefaultCoachPersona(message: String): Boolean {
        val normalized = message.lowercase()
        return listOf(
            "coach",
            "exam question",
            "practice question",
            "sie",
            "series 7",
            "series 57",
            "series 65",
            "finra",
            "nasaa"
        ).any { normalized.contains(it) }
    }

    private fun buildToolsAwarenessSuffix(toolPolicy: ToolExecutionPolicy): String {
        val parts = mutableListOf<String>()
        if (toolPolicy.webSearchEnabled) {
            parts.add("web_search (for current news, earnings, events, real-time facts)")
        }
        if (toolPolicy.yahooFinanceEnabled) {
            parts.add("yahoo-finance (for live quotes, options chains, market data)")
        }
        if (toolPolicy.internalActionsEnabled) {
            parts.add("internal metadata tools (for persona metadata and coach exam pool stats)")
        }
        if (parts.isEmpty()) return ""
        return "Live data: You have access to ${parts.joinToString(" and ")}. " +
            "When the user asks about prices, stocks, news, or anything time-sensitive, use or request these tools for current information rather than relying on training data."
    }
}

data class ChatLimitsView(
    val role: String,
    val maxOutputTokens: Int,
    val maxFileContextTokens: Int,
    val maxHistoryMessages: Int,
    val maxPromptTokens: Int,
    val dailyTokenLimit: Long,
    val usedToday: Long,
    val remainingToday: Long
)