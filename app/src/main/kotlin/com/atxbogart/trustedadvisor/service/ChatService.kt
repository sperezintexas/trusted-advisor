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
    private val personaFileService: PersonaFileService,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val grokService: GrokService,
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

        val persona = request.personaId?.let { personaService.findById(it) }
        val basePrompt = persona?.systemPrompt
            ?: "You are a helpful trusted advisor. Use provided context."

        val fileContext = request.personaId?.let {
            personaFileService.getFileContext(it, limits.maxFileContextTokens)
        } ?: ""

        val config = chatConfigService.getConfig()
        val toolPolicy = ToolExecutionPolicy(
            webSearchEnabled = (persona?.webSearchEnabled ?: true) && (config.tools["webSearch"] ?: true),
            yahooFinanceEnabled = (persona?.yahooFinanceEnabled ?: true) && (config.tools["yahooFinance"] ?: true),
            internalActionsEnabled = config.tools["internalActions"] ?: true
        )
        val toolsSuffix = buildToolsAwarenessSuffix(toolPolicy)

        val systemPrompt = buildSystemPrompt(basePrompt, fileContext, toolsSuffix)

        if (fileContext.isNotEmpty()) {
            log.debug("[chat] Including {} chars of file context for persona {}", fileContext.length, request.personaId)
        }

        val trimmedHistory = history.takeLast(limits.maxHistoryMessages)
        val messages = trimToPromptBudget(
            systemPrompt = systemPrompt,
            history = trimmedHistory,
            userMessage = request.message,
            maxPromptTokens = limits.maxPromptTokens
        )

        val grokReq = GrokRequest(
            messages = messages,
            max_tokens = limits.maxOutputTokens
        )
        val grokResponse = grokService.chatWithTools(
            request = grokReq,
            toolPolicy = toolPolicy,
            personaId = request.personaId
        )
        val responseContent = grokResponse.response

        val consumedTokens = grokResponse.usage?.total_tokens?.toLong()
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
            usage = grokResponse.usage,
            citations = grokResponse.citations,
            toolEvents = grokResponse.toolEvents
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

    private fun buildSystemPrompt(basePrompt: String, fileContext: String, toolsSuffix: String): String {
        val parts = mutableListOf(basePrompt)

        if (fileContext.isNotEmpty()) {
            parts.add("""
                |
                |## Reference Documents
                |The following documents have been attached to provide context. Use this information to answer questions accurately:
                |
                |$fileContext
            """.trimMargin())
        }

        if (toolsSuffix.isNotBlank()) {
            parts.add(toolsSuffix)
        }

        return parts.joinToString("\n\n")
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