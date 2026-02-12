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

        val systemPrompt = request.personaId?.let { id ->
            personaService.findById(id)?.systemPrompt ?: "No persona found. You are a helpful assistant."
        } ?: "You are a helpful trusted advisor. Use provided context."

        val messages = listOf(ChatMessage("system", systemPrompt)) +
                history.takeLast(10) +
                ChatMessage("user", request.message)

        val grokReq = GrokRequest(messages = messages)
        val responseContent = grokService.chat(grokReq)

        history.add(ChatMessage("user", request.message))
        history.add(ChatMessage("assistant", responseContent))

        val chatHistory = chatHistoryRepository.findByUserId(request.userId)
            ?: ChatHistory(userId = request.userId, messages = history.toMutableList())
        chatHistoryRepository.save(chatHistory.copy(updatedAt = LocalDateTime.now(ZoneOffset.UTC)))

        return ChatResponse(responseContent)
    }

    fun getHistory(userId: String): List<ChatMessage> =
        chatHistoryRepository.findByUserId(userId)?.messages ?: emptyList()
}