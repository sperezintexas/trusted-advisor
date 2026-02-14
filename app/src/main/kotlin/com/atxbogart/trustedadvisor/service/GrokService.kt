package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.ChatMessage
import com.atxbogart.trustedadvisor.model.GrokRequest
import com.atxbogart.trustedadvisor.model.GrokResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Service
class GrokService(@Value("\${xai.api.key}") private val apiKey: String) {

    private val httpClient = HttpClient.create()
        .responseTimeout(Duration.ofSeconds(120))

    private val webClient = WebClient.builder()
        .clientConnector(ReactorClientHttpConnector(httpClient))
        .baseUrl("https://api.x.ai/v1")
        .defaultHeaders {
            it.setBearerAuth(apiKey)
            it.set("Content-Type", "application/json")
        }
        .build()

    fun chat(request: GrokRequest): GrokResponse =
        webClient.post()
            .uri("/chat/completions")
            .bodyValue(request)
            .retrieve()
            .bodyToMono<GrokResponse>()
            .block()!!

    /**
     * Calls xAI with a minimal prompt to verify API key and connectivity.
     * Returns a result with success flag and optional error message.
     */
    fun testConnection(): GrokTestResult {
        return try {
            val request = GrokRequest(
                messages = listOf(ChatMessage("user", "Reply with exactly: OK")),
                max_tokens = 10
            )
            val response = chat(request)
            val content = response.choices.getOrNull(0)?.message?.content?.trim()
            if (content != null) {
                GrokTestResult(success = true, message = "Connected. Model replied: ${content.take(100)}")
            } else {
                GrokTestResult(success = false, message = "Empty response from model")
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            GrokTestResult(success = false, message = msg)
        }
    }
}

data class GrokTestResult(val success: Boolean, val message: String)