package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.*
import com.atxbogart.trustedadvisor.repository.ChatHistoryRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class ChatService(
    private val personaService: PersonaService,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val grokService: GrokService
) {

    fun sendMessage(request: ChatRequest): ChatResponse {
        val history = chatHistoryRepository.findByUserId(request.userId)?.messages ?: mutableListOf()

        val persona = request.personaId?.let { personaService.findById(it) }
        val basePrompt = persona?.systemPrompt
            ?: "You are a helpful trusted advisor. Use provided context."

        val toolsSuffix = buildToolsAwarenessSuffix(
            webSearchEnabled = persona?.webSearchEnabled ?: true,
            yahooFinanceEnabled = persona?.yahooFinanceEnabled ?: true
        )
        val systemPrompt = if (toolsSuffix.isBlank()) basePrompt else "$basePrompt\n\n$toolsSuffix"

        val messages = listOf(ChatMessage("system", systemPrompt)) +
                history.takeLast(10) +
                ChatMessage("user", request.message)

        val grokReq = GrokRequest(messages = messages)
        val grokResponse = grokService.chat(grokReq)
        val responseContent = grokResponse.choices.getOrNull(0)?.message?.content ?: ""

        history.add(ChatMessage("user", request.message))
        history.add(ChatMessage("assistant", responseContent))

        val chatHistory = chatHistoryRepository.findByUserId(request.userId)
            ?: ChatHistory(userId = request.userId, messages = history.toMutableList())
        chatHistoryRepository.save(chatHistory.copy(updatedAt = LocalDateTime.now(ZoneOffset.UTC)))

        return ChatResponse(response = responseContent, usage = grokResponse.usage)
    }

    fun getHistory(userId: String): List<ChatMessage> =
        chatHistoryRepository.findByUserId(userId)?.messages ?: emptyList()

    private fun buildToolsAwarenessSuffix(
        webSearchEnabled: Boolean,
        yahooFinanceEnabled: Boolean
    ): String {
        val parts = mutableListOf<String>()
        if (webSearchEnabled) {
            parts.add("web_search (for current news, earnings, events, real-time facts)")
        }
        if (yahooFinanceEnabled) {
            parts.add("yahoo-finance (for live quotes, options chains, market data)")
        }
        if (parts.isEmpty()) return ""
        return "Live data: You have access to ${parts.joinToString(" and ")}. " +
            "When the user asks about prices, stocks, news, or anything time-sensitive, use or request these tools for current information rather than relying on training data."
    }
}