package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionPolicyTest {

    @Test
    fun `parseRequestedTier defaults to BASIC for unknown values`() {
        assertEquals(SubscriptionTier.BASIC, SubscriptionPolicy.parseRequestedTier(null))
        assertEquals(SubscriptionTier.BASIC, SubscriptionPolicy.parseRequestedTier(""))
        assertEquals(SubscriptionTier.BASIC, SubscriptionPolicy.parseRequestedTier("enterprise"))
    }

    @Test
    fun `parseRequestedTier accepts case-insensitive premium`() {
        assertEquals(SubscriptionTier.PREMIUM, SubscriptionPolicy.parseRequestedTier("PREMIUM"))
        assertEquals(SubscriptionTier.PREMIUM, SubscriptionPolicy.parseRequestedTier("premium"))
        assertEquals(SubscriptionTier.PREMIUM, SubscriptionPolicy.parseRequestedTier(" Premium "))
    }

    @Test
    fun `resolveUserRole keeps admin sticky`() {
        assertEquals(
            UserRole.ADMIN,
            SubscriptionPolicy.resolveUserRole(UserRole.ADMIN, "BASIC")
        )
        assertEquals(
            UserRole.ADMIN,
            SubscriptionPolicy.resolveUserRole(UserRole.ADMIN, "PREMIUM")
        )
    }

    @Test
    fun `resolveUserRole maps non-admin tier to expected role`() {
        assertEquals(
            UserRole.BASIC,
            SubscriptionPolicy.resolveUserRole(UserRole.BASIC, "BASIC")
        )
        assertEquals(
            UserRole.PREMIUM,
            SubscriptionPolicy.resolveUserRole(UserRole.BASIC, "PREMIUM")
        )
        assertEquals(
            UserRole.BASIC,
            SubscriptionPolicy.resolveUserRole(UserRole.PREMIUM, "unknown")
        )
    }

    @Test
    fun `availablePlans exposes basic and premium plans`() {
        val plans = SubscriptionPolicy.availablePlans()
        assertEquals(2, plans.size)
        assertTrue(plans.any { it.tier == SubscriptionTier.BASIC && it.monthlyPriceUsd == "0.00" })
        assertTrue(plans.any { it.tier == SubscriptionTier.PREMIUM && it.monthlyPriceUsd == "9.99" })
    }
}
