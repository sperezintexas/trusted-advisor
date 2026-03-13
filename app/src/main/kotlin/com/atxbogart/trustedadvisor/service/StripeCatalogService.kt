package com.atxbogart.trustedadvisor.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

data class StripePlanView(
    val tier: String,
    val displayName: String,
    val monthlyPriceUsd: String,
    val features: List<String>,
    val stripeProductId: String? = null,
    val stripePriceId: String? = null,
    val source: String = "stripe"
)

@Service
class StripeCatalogService(
    @Value("\${stripe.secret-key:}") private val stripeSecretKey: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    private val webClient = WebClient.builder()
        .baseUrl("https://api.stripe.com/v1")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
        .build()

    fun isConfigured(): Boolean = stripeSecretKey.isNotBlank()

    fun loadPlansFromStripe(fallbackPlans: List<SubscriptionPlan>): List<StripePlanView>? {
        if (!isConfigured()) return null

        return try {
            val productsJson = webClient.get()
                .uri("/products?active=true&limit=100")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $stripeSecretKey")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
                ?: return null

            val pricesJson = webClient.get()
                .uri("/prices?active=true&type=recurring&limit=100")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $stripeSecretKey")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
                ?: return null

            val productsRoot = objectMapper.readValue(productsJson, Map::class.java)
            val pricesRoot = objectMapper.readValue(pricesJson, Map::class.java)
            val products = (productsRoot["data"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
            val prices = (pricesRoot["data"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()

            val productsById = products.associateBy { (it["id"] as? String).orEmpty() }
            val fallbackByTier = fallbackPlans.associateBy { it.tier.name }
            val plans = mutableListOf<StripePlanView>()

            for (price in prices) {
                val recurring = price["recurring"] as? Map<*, *> ?: continue
                val interval = recurring["interval"] as? String ?: continue
                if (interval != "month") continue

                val productId = price["product"] as? String ?: continue
                val product = productsById[productId] ?: continue

                val metadata = product["metadata"] as? Map<*, *> ?: emptyMap<Any, Any>()
                val tierRaw = (metadata["tier"] as? String)
                    ?: (product["name"] as? String)
                    ?: continue
                val tier = resolveTier(tierRaw) ?: continue

                val amountCents = (price["unit_amount"] as? Number)?.toLong() ?: continue
                val monthlyPriceUsd = "%.2f".format(amountCents / 100.0)
                val displayName = (product["name"] as? String)?.ifBlank { null }
                    ?: fallbackByTier[tier]?.displayName
                    ?: tier.lowercase().replaceFirstChar { it.titlecase() }
                val features = ((metadata["features"] as? String)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.takeIf { it.isNotEmpty() })
                    ?: fallbackByTier[tier]?.features
                    ?: emptyList()

                plans.add(
                    StripePlanView(
                        tier = tier,
                        displayName = displayName,
                        monthlyPriceUsd = monthlyPriceUsd,
                        features = features,
                        stripeProductId = productId,
                        stripePriceId = price["id"] as? String,
                        source = "stripe"
                    )
                )
            }

            // De-duplicate by tier, keeping the cheapest monthly price if multiple active prices exist.
            plans.groupBy { it.tier }
                .mapNotNull { (_, tierPlans) ->
                    tierPlans.minByOrNull { it.monthlyPriceUsd.toDoubleOrNull() ?: Double.MAX_VALUE }
                }
                .sortedBy { if (it.tier == "BASIC") 0 else 1 }
                .takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            log.warn("[stripe-catalog] Failed to fetch Stripe products/prices: {}", e.message)
            null
        }
    }

    private fun resolveTier(raw: String): String? {
        val n = raw.trim().uppercase()
        return when {
            n == "BASIC" || n.contains("BASIC") || n.contains("FREE") -> "BASIC"
            n == "PREMIUM" || n.contains("PREMIUM") || n.contains("PRO") -> "PREMIUM"
            else -> null
        }
    }
}

