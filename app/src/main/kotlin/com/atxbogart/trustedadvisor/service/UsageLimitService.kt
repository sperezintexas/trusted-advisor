package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.UserRole
import com.atxbogart.trustedadvisor.repository.ChatHistoryRepository
import com.atxbogart.trustedadvisor.repository.CoachUserProgressRepository
import com.atxbogart.trustedadvisor.repository.UserRepository
import org.springframework.stereotype.Service

data class UsageStatus(
    val role: UserRole,
    val used: Int,
    val limit: Int?,
    val remaining: Int?
) {
    val isLimited: Boolean get() = limit != null
    val isAtLimit: Boolean get() = limit != null && used >= limit
}

@Service
class UsageLimitService(
    private val userRepository: UserRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val coachUserProgressRepository: CoachUserProgressRepository
) {
    companion object {
        const val BASIC_CHAT_QUESTION_LIMIT = 10
        const val BASIC_COACH_QUESTION_LIMIT = 30
    }

    fun chatUsageStatus(userId: String): UsageStatus {
        val role = resolveRole(userId)
        val used = chatHistoryRepository.findByUserId(userId)
            ?.messages
            ?.count { it.role == "user" }
            ?: 0
        return when (role) {
            UserRole.BASIC -> UsageStatus(
                role = role,
                used = used,
                limit = BASIC_CHAT_QUESTION_LIMIT,
                remaining = (BASIC_CHAT_QUESTION_LIMIT - used).coerceAtLeast(0)
            )
            UserRole.PREMIUM, UserRole.ADMIN -> UsageStatus(
                role = role,
                used = used,
                limit = null,
                remaining = null
            )
        }
    }

    fun coachUsageStatus(userId: String): UsageStatus {
        val role = resolveRole(userId)
        val used = coachUserProgressRepository.findByUserId(userId)
            .sumOf { it.totalAsked }
        return when (role) {
            UserRole.BASIC -> UsageStatus(
                role = role,
                used = used,
                limit = BASIC_COACH_QUESTION_LIMIT,
                remaining = (BASIC_COACH_QUESTION_LIMIT - used).coerceAtLeast(0)
            )
            UserRole.PREMIUM, UserRole.ADMIN -> UsageStatus(
                role = role,
                used = used,
                limit = null,
                remaining = null
            )
        }
    }

    private fun resolveRole(userId: String): UserRole {
        val user = userRepository.findByEmail(userId)
        return user?.role ?: UserRole.BASIC
    }
}
