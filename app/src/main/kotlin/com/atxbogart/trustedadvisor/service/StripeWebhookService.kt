package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.UserRole
import com.atxbogart.trustedadvisor.repository.UserRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class StripeWebhookService(
    @Value("\${stripe.webhook-secret:}") private val webhookSecret: String,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    fun handleWebhook(payload: String, stripeSignature: String?): Pair<Boolean, String> {
        if (webhookSecret.isBlank()) {
            return false to "Stripe webhook secret not configured"
        }
        if (stripeSignature.isNullOrBlank()) {
            return false to "Missing Stripe-Signature header"
        }
        if (!verifySignature(payload, stripeSignature, webhookSecret)) {
            return false to "Invalid Stripe signature"
        }

        return try {
            val root = objectMapper.readValue(payload, Map::class.java)
            val type = root["type"] as? String ?: return false to "Missing event type"
            val data = root["data"] as? Map<*, *> ?: return false to "Missing data object"
            val obj = data["object"] as? Map<*, *> ?: return false to "Missing event object"

            when (type) {
                "checkout.session.completed" -> {
                    handleCheckoutCompleted(obj)
                    true to "Processed checkout.session.completed"
                }
                "customer.subscription.created",
                "customer.subscription.updated",
                "customer.subscription.deleted" -> {
                    handleSubscriptionChanged(obj, type)
                    true to "Processed $type"
                }
                else -> true to "Ignored event type: $type"
            }
        } catch (e: Exception) {
            log.warn("[stripe-webhook] Failed to process event: {}", e.message)
            false to (e.message ?: "Webhook processing failed")
        }
    }

    private fun handleCheckoutCompleted(session: Map<*, *>) {
        val customerEmail = ((session["customer_details"] as? Map<*, *>)?.get("email") as? String)
            ?: (session["customer_email"] as? String)
            ?: ((session["metadata"] as? Map<*, *>)?.get("email") as? String)
            ?: return
        val customerId = session["customer"] as? String
        val subscriptionId = session["subscription"] as? String
        val mode = session["mode"] as? String
        if (mode != "subscription") return

        val user = userRepository.findByEmail(customerEmail) ?: return
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val updated = user.copy(
            role = if (user.role == UserRole.ADMIN) UserRole.ADMIN else UserRole.PREMIUM,
            stripeCustomerId = customerId ?: user.stripeCustomerId,
            stripeSubscriptionId = subscriptionId ?: user.stripeSubscriptionId,
            stripeSubscriptionStatus = "active",
            updatedAt = now
        )
        userRepository.save(updated)
        log.info("[stripe-webhook] Upgraded user {} to PREMIUM via checkout completion", customerEmail)
    }

    private fun handleSubscriptionChanged(subscription: Map<*, *>, eventType: String) {
        val customerId = subscription["customer"] as? String ?: return
        val subscriptionId = subscription["id"] as? String
        val status = subscription["status"] as? String ?: "unknown"

        val user = userRepository.findByStripeCustomerId(customerId) ?: run {
            log.info("[stripe-webhook] No user mapped for customer {}", customerId)
            return
        }
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val shouldBePremium = status in setOf("trialing", "active", "past_due", "incomplete")
        val nextRole = if (user.role == UserRole.ADMIN) UserRole.ADMIN else if (shouldBePremium) UserRole.PREMIUM else UserRole.BASIC

        val updated = user.copy(
            role = nextRole,
            stripeSubscriptionId = subscriptionId ?: user.stripeSubscriptionId,
            stripeSubscriptionStatus = status,
            updatedAt = now
        )
        userRepository.save(updated)
        log.info("[stripe-webhook] Updated user {} role={} status={} event={}", user.email, nextRole, status, eventType)
    }

    private fun verifySignature(payload: String, stripeSignature: String, secret: String): Boolean {
        val pairs = stripeSignature.split(",").mapNotNull {
            val idx = it.indexOf("=")
            if (idx <= 0) null else it.substring(0, idx) to it.substring(idx + 1)
        }.toMap()
        val timestamp = pairs["t"]?.toLongOrNull() ?: return false
        val v1 = pairs["v1"] ?: return false

        // 5-minute tolerance to reduce replay window.
        val now = Instant.now().epochSecond
        if (kotlin.math.abs(now - timestamp) > 300) return false

        val signedPayload = "$timestamp.$payload"
        val computed = hmacSha256(secret, signedPayload)
        return constantTimeEquals(computed, v1)
    }

    private fun hmacSha256(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}

