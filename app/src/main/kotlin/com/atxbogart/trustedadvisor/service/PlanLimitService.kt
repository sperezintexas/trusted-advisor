package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.UserRole
import org.springframework.stereotype.Service

data class PlanLimits(
    val maxOutputTokens: Int,
    val maxFileContextTokens: Int,
    val maxHistoryMessages: Int,
    val maxPromptTokens: Int,
    val dailyTokenLimit: Long
)

@Service
class PlanLimitService {
    fun limitsForRole(role: UserRole): PlanLimits =
        when (role) {
            UserRole.ADMIN -> PlanLimits(
                maxOutputTokens = 4096,
                maxFileContextTokens = 10000,
                maxHistoryMessages = 20,
                maxPromptTokens = 14000,
                dailyTokenLimit = 1_000_000L
            )
            UserRole.PREMIUM -> PlanLimits(
                maxOutputTokens = 2048,
                maxFileContextTokens = 6000,
                maxHistoryMessages = 14,
                maxPromptTokens = 10000,
                dailyTokenLimit = 300_000L
            )
            UserRole.BASIC -> PlanLimits(
                maxOutputTokens = 512,
                maxFileContextTokens = 1200,
                maxHistoryMessages = 6,
                maxPromptTokens = 4000,
                dailyTokenLimit = 40_000L
            )
        }
}
