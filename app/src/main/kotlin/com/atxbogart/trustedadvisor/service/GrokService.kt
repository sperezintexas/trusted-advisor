package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.GrokRequest
import com.atxbogart.trustedadvisor.model.GrokResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
@Service
class GrokService(@Value("\${xai.api.key}") private val apiKey: String) {

    private val webClient = WebClient.builder()
        .baseUrl("https://api.x.ai/v1")
        .defaultHeaders {
            it.setBearerAuth(apiKey)
            it.set("Content-Type", "application/json")
        }
        .build()

    fun chat(request: GrokRequest): String = 
        webClient.post()
            .uri("/chat/completions")
            .bodyValue(request)
            .retrieve()
            .bodyToMono<GrokResponse>()
            .block()!!
            .choices[0].message.content
}