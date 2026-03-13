package com.atxbogart.trustedadvisor.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class CreateCheckoutSessionResult(
    val checkoutUrl: String,
    val sessionId: String
)

data class CheckoutVerificationResult(
    val valid: Boolean,
    val message: String
)

@Service
class StripeCheckoutService(
    @Value("\${stripe.secret-key:}") private val stripeSecretKey: String,
    @Value("\${stripe.prices.basic:}") private val basicPriceId: String,
    @Value("\${stripe.prices.premium:}") private val premiumPriceId: String,
    @Value("\${stripe.products.basic:}") private val basicProductId: String,
    @Value("\${stripe.products.premium:}") private val premiumProductId: String,
    @Value("\${app.frontend-url:http://localhost:3000}") private val frontendUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    private val webClient = WebClient.builder()
        .baseUrl("https://api.stripe.com/v1")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
        .build()

    fun isConfigured(): Boolean = stripeSecretKey.isNotBlank()

    fun getConfiguredPriceIdForTier(tier: String): String? = when (tier.uppercase()) {
        "BASIC" -> basicPriceId.ifBlank { null }
        "PREMIUM" -> premiumPriceId.ifBlank { null }
        else -> null
    }

    fun getConfiguredProductIdForTier(tier: String): String? = when (tier.uppercase()) {
        "BASIC" -> basicProductId.ifBlank { null }
        "PREMIUM" -> premiumProductId.ifBlank { null }
        else -> null
    }

    fun createCheckoutSession(
        tier: String,
        email: String,
        username: String,
        displayName: String
    ): CreateCheckoutSessionResult? {
        if (!isConfigured()) return null
        val priceId = getConfiguredPriceIdForTier(tier) ?: return null

        val successUrl = "${frontendUrl.trimEnd('/')}/register?checkout=success&session_id={CHECKOUT_SESSION_ID}"
        val cancelUrl = "${frontendUrl.trimEnd('/')}/register?checkout=cancelled"

        val form = buildString {
            append("mode=subscription")
            append("&line_items[0][price]=").append(urlEncode(priceId))
            append("&line_items[0][quantity]=1")
            append("&success_url=").append(urlEncode(successUrl))
            append("&cancel_url=").append(urlEncode(cancelUrl))
            append("&customer_email=").append(urlEncode(email))
            append("&metadata[tier]=").append(urlEncode(tier.uppercase()))
            append("&metadata[email]=").append(urlEncode(email))
            append("&metadata[username]=").append(urlEncode(username))
            append("&metadata[displayName]=").append(urlEncode(displayName))
        }

        return try {
            val response = webClient.post()
                .uri("/checkout/sessions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $stripeSecretKey")
                .bodyValue(form)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
                ?: return null

            val map = objectMapper.readValue(response, Map::class.java)
            val url = map["url"] as? String ?: return null
            val sessionId = map["id"] as? String ?: return null
            CreateCheckoutSessionResult(checkoutUrl = url, sessionId = sessionId)
        } catch (e: Exception) {
            log.warn("[stripe-checkout] Failed to create checkout session: {}", e.message)
            null
        }
    }

    fun verifyCheckoutSession(sessionId: String, expectedEmail: String): CheckoutVerificationResult {
        if (!isConfigured()) {
            return CheckoutVerificationResult(false, "Stripe is not configured")
        }
        return try {
            val response = webClient.get()
                .uri("/checkout/sessions/${urlEncodePath(sessionId)}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $stripeSecretKey")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
                ?: return CheckoutVerificationResult(false, "No response from Stripe")

            val map = objectMapper.readValue(response, Map::class.java)
            val status = map["status"] as? String
            val paymentStatus = map["payment_status"] as? String
            val customerDetails = map["customer_details"] as? Map<*, *>
            val customerEmail = (customerDetails?.get("email") as? String)?.lowercase()
            val expected = expectedEmail.lowercase()
            val metadata = map["metadata"] as? Map<*, *>
            val metadataEmail = (metadata?.get("email") as? String)?.lowercase()

            val emailMatches = customerEmail == expected || metadataEmail == expected
            if (!emailMatches) {
                return CheckoutVerificationResult(false, "Checkout session email mismatch")
            }
            if (status != "complete") {
                return CheckoutVerificationResult(false, "Checkout session is not complete")
            }
            if (paymentStatus != "paid" && paymentStatus != "no_payment_required") {
                return CheckoutVerificationResult(false, "Payment not completed")
            }
            CheckoutVerificationResult(true, "Verified")
        } catch (e: Exception) {
            log.warn("[stripe-checkout] Failed to verify checkout session {}: {}", sessionId, e.message)
            CheckoutVerificationResult(false, e.message ?: "Verification failed")
        }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun urlEncodePath(value: String): String =
        value.replace("/", "%2F")
}

