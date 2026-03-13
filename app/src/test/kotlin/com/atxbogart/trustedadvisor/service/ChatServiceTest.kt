package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.ChatHistory
import com.atxbogart.trustedadvisor.model.ChatRequest
import com.atxbogart.trustedadvisor.model.ChatResponse
import com.atxbogart.trustedadvisor.model.GrokUsage
import com.atxbogart.trustedadvisor.model.Persona
import com.atxbogart.trustedadvisor.model.ToolExecutionPolicy
import com.atxbogart.trustedadvisor.model.User
import com.atxbogart.trustedadvisor.model.UserRole
import com.atxbogart.trustedadvisor.repository.ChatHistoryRepository
import com.atxbogart.trustedadvisor.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ChatServiceTest {

    private val personaService = mock(PersonaService::class.java)
    private val chatHistoryRepository = mock(ChatHistoryRepository::class.java)
    private val agentOrchestratorService = mock(AgentOrchestratorService::class.java)
    private val chatConfigService = ChatConfigService()
    private val userRepository = mock(UserRepository::class.java)
    private val planLimitService = PlanLimitService()
    private val tokenUsageService = mock(TokenUsageService::class.java)

    private val service = ChatService(
        personaService = personaService,
        chatHistoryRepository = chatHistoryRepository,
        agentOrchestratorService = agentOrchestratorService,
        chatConfigService = chatConfigService,
        userRepository = userRepository,
        planLimitService = planLimitService,
        tokenUsageService = tokenUsageService
    )

    @Test
    fun `coach exam prompts use finance-coach as default persona`() {
        val userId = "coach-user@example.com"
        val defaultCoachPersona = Persona(
            id = "finance-coach",
            name = "Finance Coach",
            description = "Coach persona",
            systemPrompt = "You are the finance coach default persona.",
            webSearchEnabled = true,
            yahooFinanceEnabled = true
        )

        `when`(userRepository.findByEmail(userId)).thenReturn(
            User(email = userId, username = "coach-user", role = UserRole.BASIC)
        )
        `when`(chatHistoryRepository.findByUserId(userId)).thenReturn(null)
        doReturn(0L).`when`(tokenUsageService).getTodayUsage(userId)
        doReturn(100L).`when`(tokenUsageService).addTodayUsage(userId, 100L)
        `when`(personaService.findDefaultCoachPersona()).thenReturn(defaultCoachPersona)
        val expectedRequest = AgentOrchestratorRequest(
            userMessage = "Give me a SIE practice exam question",
            personaId = "finance-coach",
            systemPrompt = "You are the finance coach default persona.\n\nLive data: You have access to internal metadata tools (for persona metadata and coach exam pool stats). When the user asks about prices, stocks, news, or anything time-sensitive, use or request these tools for current information rather than relying on training data.",
            history = emptyList(),
            maxOutputTokens = 512,
            toolPolicy = ToolExecutionPolicy(
                webSearchEnabled = false,
                yahooFinanceEnabled = false,
                internalActionsEnabled = true
            )
        )
        `when`(agentOrchestratorService.orchestrate(expectedRequest)).thenReturn(
            AgentOrchestratorResult(
                response = "Here is your SIE question.",
                usage = GrokUsage(total_tokens = 100)
            )
        )
        `when`(chatHistoryRepository.save(any(ChatHistory::class.java))).thenAnswer { it.arguments[0] as ChatHistory }

        val response: ChatResponse = service.sendMessage(
            ChatRequest(
                message = "Give me a SIE practice exam question",
                personaId = null,
                userId = userId
            )
        )

        assertEquals("Here is your SIE question.", response.response)
        verify(personaService).findDefaultCoachPersona()
        verify(agentOrchestratorService).orchestrate(expectedRequest)
    }

    @Test
    fun `explicit persona id is used instead of finance-coach default`() {
        val userId = "coach-user@example.com"
        val explicitPersona = Persona(
            id = "persona-123",
            name = "Custom Persona",
            description = "Custom",
            systemPrompt = "You are the custom persona.",
            webSearchEnabled = false,
            yahooFinanceEnabled = false
        )

        `when`(userRepository.findByEmail(userId)).thenReturn(
            User(email = userId, username = "coach-user", role = UserRole.BASIC)
        )
        `when`(chatHistoryRepository.findByUserId(userId)).thenReturn(null)
        doReturn(0L).`when`(tokenUsageService).getTodayUsage(userId)
        doReturn(100L).`when`(tokenUsageService).addTodayUsage(userId, 100L)
        `when`(personaService.findById("persona-123")).thenReturn(explicitPersona)
        val expectedRequest = AgentOrchestratorRequest(
            userMessage = "Give me a Series 7 exam question",
            personaId = "persona-123",
            systemPrompt = "You are the custom persona.\n\nLive data: You have access to internal metadata tools (for persona metadata and coach exam pool stats). When the user asks about prices, stocks, news, or anything time-sensitive, use or request these tools for current information rather than relying on training data.",
            history = emptyList(),
            maxOutputTokens = 512,
            toolPolicy = ToolExecutionPolicy(
                webSearchEnabled = false,
                yahooFinanceEnabled = false,
                internalActionsEnabled = true
            )
        )
        `when`(agentOrchestratorService.orchestrate(expectedRequest)).thenReturn(
            AgentOrchestratorResult(
                response = "Using custom persona.",
                usage = GrokUsage(total_tokens = 100)
            )
        )
        `when`(chatHistoryRepository.save(any(ChatHistory::class.java))).thenAnswer { it.arguments[0] as ChatHistory }

        service.sendMessage(
            ChatRequest(
                message = "Give me a Series 7 exam question",
                personaId = "persona-123",
                userId = userId
            )
        )

        verify(agentOrchestratorService).orchestrate(expectedRequest)
        verify(personaService, never()).findDefaultCoachPersona()
        assertTrue(expectedRequest.systemPrompt.contains("custom persona"))
    }
}
