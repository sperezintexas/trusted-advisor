package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.ChatHistory
import com.atxbogart.trustedadvisor.model.ChatMessage
import com.atxbogart.trustedadvisor.model.CoachUserProgress
import com.atxbogart.trustedadvisor.model.ExamCode
import com.atxbogart.trustedadvisor.model.User
import com.atxbogart.trustedadvisor.model.UserRole
import com.atxbogart.trustedadvisor.repository.ChatHistoryRepository
import com.atxbogart.trustedadvisor.repository.CoachUserProgressRepository
import com.atxbogart.trustedadvisor.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class UsageLimitServiceTest {

    private val userRepository = mock(UserRepository::class.java)
    private val chatHistoryRepository = mock(ChatHistoryRepository::class.java)
    private val coachUserProgressRepository = mock(CoachUserProgressRepository::class.java)

    private val service = UsageLimitService(
        userRepository = userRepository,
        chatHistoryRepository = chatHistoryRepository,
        coachUserProgressRepository = coachUserProgressRepository
    )

    @Test
    fun `basic chat has limit of ten`() {
        val userId = "basic@example.com"
        `when`(userRepository.findByEmail(userId)).thenReturn(
            User(email = userId, username = "basic", role = UserRole.BASIC)
        )
        `when`(chatHistoryRepository.findByUserId(userId)).thenReturn(
            ChatHistory(
                userId = userId,
                messages = mutableListOf(
                    ChatMessage("user", "q1"),
                    ChatMessage("assistant", "a1"),
                    ChatMessage("user", "q2")
                )
            )
        )

        val usage = service.chatUsageStatus(userId)
        assertEquals(UserRole.BASIC, usage.role)
        assertEquals(2, usage.used)
        assertEquals(UsageLimitService.BASIC_CHAT_QUESTION_LIMIT, usage.limit)
        assertEquals(8, usage.remaining)
        assertFalse(usage.isAtLimit)
    }

    @Test
    fun `premium chat is unlimited`() {
        val userId = "premium@example.com"
        `when`(userRepository.findByEmail(userId)).thenReturn(
            User(email = userId, username = "premium", role = UserRole.PREMIUM)
        )
        `when`(chatHistoryRepository.findByUserId(userId)).thenReturn(
            ChatHistory(
                userId = userId,
                messages = MutableList(50) { idx ->
                    if (idx % 2 == 0) ChatMessage("user", "q$idx") else ChatMessage("assistant", "a$idx")
                }
            )
        )

        val usage = service.chatUsageStatus(userId)
        assertEquals(UserRole.PREMIUM, usage.role)
        assertEquals(null, usage.limit)
        assertEquals(null, usage.remaining)
        assertFalse(usage.isAtLimit)
    }

    @Test
    fun `basic coach has limit of thirty questions across exams`() {
        val userId = "basic-coach@example.com"
        `when`(userRepository.findByEmail(userId)).thenReturn(
            User(email = userId, username = "basic-coach", role = UserRole.BASIC)
        )
        `when`(coachUserProgressRepository.findByUserId(userId)).thenReturn(
            listOf(
                CoachUserProgress(userId = userId, examCode = ExamCode.SIE, totalAsked = 10),
                CoachUserProgress(userId = userId, examCode = ExamCode.SERIES_7, totalAsked = 20)
            )
        )

        val usage = service.coachUsageStatus(userId)
        assertEquals(30, usage.used)
        assertEquals(UsageLimitService.BASIC_COACH_QUESTION_LIMIT, usage.limit)
        assertEquals(0, usage.remaining)
        assertTrue(usage.isAtLimit)
    }
}
