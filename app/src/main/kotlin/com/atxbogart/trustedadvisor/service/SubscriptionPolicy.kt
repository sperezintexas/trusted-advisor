package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.UserRole

enum class SubscriptionTier {
    BASIC,
    PREMIUM
}

data class SubscriptionPlan(
    val tier: SubscriptionTier,
    val displayName: String,
    val monthlyPriceUsd: String,
    val features: List<String>
)

object SubscriptionPolicy {
    private val plans = listOf(
        SubscriptionPlan(
            tier = SubscriptionTier.BASIC,
            displayName = "Basic",
            monthlyPriceUsd = "0.00",
            features = listOf(
                "Exam Coach access",
                "Practice exams",
                "Basic chat"
            )
        ),
        SubscriptionPlan(
            tier = SubscriptionTier.PREMIUM,
            displayName = "Premium",
            monthlyPriceUsd = "9.99",
            features = listOf(
                "Everything in Basic",
                "AI Tutor sessions",
                "Priority support"
            )
        )
    )

    fun availablePlans(): List<SubscriptionPlan> = plans

    fun parseRequestedTier(rawTier: String?): SubscriptionTier {
        val normalized = rawTier?.trim()?.uppercase().orEmpty()
        return when (normalized) {
            SubscriptionTier.PREMIUM.name -> SubscriptionTier.PREMIUM
            else -> SubscriptionTier.BASIC
        }
    }

    /**
     * Admin role is sticky: registration cannot demote admins to paid/free tiers.
     */
    fun resolveUserRole(existingRole: UserRole, rawTier: String?): UserRole {
        if (existingRole == UserRole.ADMIN) return UserRole.ADMIN
        return when (parseRequestedTier(rawTier)) {
            SubscriptionTier.BASIC -> UserRole.BASIC
            SubscriptionTier.PREMIUM -> UserRole.PREMIUM
        }
    }
}
