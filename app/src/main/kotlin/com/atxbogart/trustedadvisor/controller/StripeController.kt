package com.atxbogart.trustedadvisor.controller

import com.atxbogart.trustedadvisor.service.StripeWebhookService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/stripe")
class StripeController(
    private val stripeWebhookService: StripeWebhookService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/webhook")
    fun webhook(
        @RequestBody payload: String,
        @RequestHeader("Stripe-Signature", required = false) stripeSignature: String?
    ): ResponseEntity<Map<String, Any>> {
        val (ok, message) = stripeWebhookService.handleWebhook(payload, stripeSignature)
        return if (ok) {
            ResponseEntity.ok(mapOf("received" to true, "message" to message))
        } else {
            log.warn("[stripe-webhook] Rejected webhook: {}", message)
            ResponseEntity.badRequest().body(mapOf("received" to false, "message" to message))
        }
    }
}

