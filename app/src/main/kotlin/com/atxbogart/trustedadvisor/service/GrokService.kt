package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Service
class GrokService(
    @Value("\${xai.api.key}") private val apiKey: String,
    private val personaService: PersonaService,
    private val coachService: CoachService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val maxToolRounds = 4

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

    fun chatWithTools(
        request: GrokRequest,
        toolPolicy: ToolExecutionPolicy,
        personaId: String?
    ): ToolCapableChatResult {
        val toolDefs = buildToolDefinitions(toolPolicy)
        if (toolDefs.isEmpty()) {
            val fallback = chat(request)
            val content = fallback.choices.getOrNull(0)?.message?.content.orEmpty()
            return ToolCapableChatResult(response = content, usage = fallback.usage)
        }

        return try {
            executeResponsesFlow(request, toolDefs, personaId)
        } catch (e: WebClientResponseException) {
            // Fallback to chat/completions if responses endpoint or payload is unavailable.
            if (e.statusCode.is4xxClientError || e.statusCode.is5xxServerError) {
                log.warn("responses API failed ({}), falling back to chat/completions: {}", e.statusCode, e.message)
                val fallback = chat(request)
                val content = fallback.choices.getOrNull(0)?.message?.content.orEmpty()
                ToolCapableChatResult(response = content, usage = fallback.usage)
            } else {
                throw e
            }
        }
    }

    private fun executeResponsesFlow(
        request: GrokRequest,
        toolDefs: List<Map<String, Any>>,
        personaId: String?
    ): ToolCapableChatResult {
        val toolEvents = mutableListOf<ChatToolEvent>()
        val citations = linkedMapOf<String, ChatCitation>()
        var latestResponseNode = postResponses(
            mutableMapOf(
                "model" to request.model,
                "input" to request.messages.map { mapOf("role" to it.role, "content" to it.content) },
                "temperature" to request.temperature,
                "max_output_tokens" to request.max_tokens,
                "tools" to toolDefs
            )
        )

        var rounds = 0
        while (rounds < maxToolRounds) {
            rounds++
            collectToolEvents(latestResponseNode, toolEvents)
            collectCitations(latestResponseNode, citations)

            val functionCalls = extractFunctionCalls(latestResponseNode)
            if (functionCalls.isEmpty()) break

            val responseId = latestResponseNode.path("id").asText("")
            if (responseId.isBlank()) break

            val functionOutputs = functionCalls.map { call ->
                val outputPayload = executeFunctionTool(
                    name = call.name,
                    argumentsJson = call.argumentsJson,
                    personaId = personaId
                )
                val outputJson = objectMapper.writeValueAsString(outputPayload)
                toolEvents.add(
                    ChatToolEvent(
                        type = "function_call_output",
                        name = call.name,
                        status = "completed",
                        callId = call.callId,
                        arguments = truncate(call.argumentsJson, 2000),
                        output = truncate(outputJson, 3000)
                    )
                )
                mapOf(
                    "type" to "function_call_output",
                    "call_id" to call.callId,
                    "output" to outputJson
                )
            }

            latestResponseNode = postResponses(
                mutableMapOf(
                    "model" to request.model,
                    "previous_response_id" to responseId,
                    "input" to functionOutputs,
                    "tools" to toolDefs
                )
            )
        }

        val responseText = extractText(latestResponseNode)
        val usage = extractUsage(latestResponseNode)
        return ToolCapableChatResult(
            response = responseText,
            usage = usage,
            citations = citations.values.toList(),
            toolEvents = toolEvents
        )
    }

    private fun postResponses(payload: MutableMap<String, Any>): JsonNode =
        webClient.post()
            .uri("/responses")
            .bodyValue(payload)
            .retrieve()
            .bodyToMono<JsonNode>()
            .block()!!

    private fun buildToolDefinitions(policy: ToolExecutionPolicy): List<Map<String, Any>> {
        val defs = mutableListOf<Map<String, Any>>()
        if (policy.webSearchEnabled) {
            defs.add(mapOf("type" to "web_search"))
        }
        if (policy.internalActionsEnabled) {
            defs.add(
                mapOf(
                    "type" to "function",
                    "name" to "fetch_persona_metadata",
                    "description" to "Get internal persona metadata and tool flags by personaId.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "personaId" to mapOf(
                                "type" to "string",
                                "description" to "Persona identifier"
                            )
                        ),
                        "required" to listOf("personaId"),
                        "additionalProperties" to false
                    )
                )
            )
            defs.add(
                mapOf(
                    "type" to "function",
                    "name" to "get_exam_pool_stats",
                    "description" to "Get active coach question pool counts per exam code.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "examCode" to mapOf(
                                "type" to "string",
                                "enum" to ExamCode.entries.map { it.name },
                                "description" to "Optional exam code filter"
                            )
                        ),
                        "additionalProperties" to false
                    )
                )
            )
        }
        return defs
    }

    private fun extractFunctionCalls(responseNode: JsonNode): List<FunctionCall> {
        val out = mutableListOf<FunctionCall>()
        val output = responseNode.path("output")
        if (!output.isArray) return out
        output.forEach { item ->
            if (item.path("type").asText() == "function_call") {
                val callId = item.path("call_id").asText("").ifBlank {
                    item.path("id").asText("")
                }
                if (callId.isBlank()) return@forEach
                out.add(
                    FunctionCall(
                        callId = callId,
                        name = item.path("name").asText(""),
                        argumentsJson = item.path("arguments").asText("{}")
                    )
                )
            }
        }
        return out
    }

    private fun collectToolEvents(responseNode: JsonNode, collector: MutableList<ChatToolEvent>) {
        val output = responseNode.path("output")
        if (!output.isArray) return
        output.forEach { item ->
            val type = item.path("type").asText("")
            if (type == "message" || type.isBlank()) return@forEach
            if (type == "function_call") {
                collector.add(
                    ChatToolEvent(
                        type = type,
                        name = textOrNull(item.path("name")),
                        status = textOrNull(item.path("status")),
                        callId = textOrNull(item.path("call_id")),
                        arguments = truncate(textOrNull(item.path("arguments")), 2000)
                    )
                )
            } else {
                collector.add(
                    ChatToolEvent(
                        type = type,
                        name = textOrNull(item.path("name")),
                        status = textOrNull(item.path("status")),
                        callId = textOrNull(item.path("id")),
                        arguments = truncate(item.toString(), 1000)
                    )
                )
            }
        }
    }

    private fun collectCitations(responseNode: JsonNode, citations: MutableMap<String, ChatCitation>) {
        val output = responseNode.path("output")
        if (!output.isArray) return
        output.forEach itemLoop@{ item ->
            val content = item.path("content")
            if (!content.isArray) return@itemLoop
            content.forEach contentLoop@{ contentItem ->
                val annotations = contentItem.path("annotations")
                if (!annotations.isArray) return@contentLoop
                annotations.forEach annotationLoop@{ annotation ->
                    val url = textOrNull(annotation.path("url"))
                        ?: textOrNull(annotation.path("source").path("url"))
                    if (url.isNullOrBlank()) return@annotationLoop
                    citations[url] = ChatCitation(
                        url = url,
                        title = textOrNull(annotation.path("title"))
                            ?: textOrNull(annotation.path("source").path("title")),
                        sourceType = textOrNull(annotation.path("type"))
                    )
                }
            }
        }
    }

    private fun extractText(responseNode: JsonNode): String {
        val direct = responseNode.path("output_text").asText("")
        if (direct.isNotBlank()) return direct

        val lines = mutableListOf<String>()
        val output = responseNode.path("output")
        if (!output.isArray) return ""
        output.forEach { item ->
            if (item.path("type").asText() != "message") return@forEach
            val content = item.path("content")
            if (!content.isArray) return@forEach
            content.forEach { contentItem ->
                val type = contentItem.path("type").asText()
                if (type == "output_text" || type == "text") {
                    val txt = contentItem.path("text").asText("")
                    if (txt.isNotBlank()) lines.add(txt)
                }
            }
        }
        return lines.joinToString("\n").trim()
    }

    private fun extractUsage(responseNode: JsonNode): GrokUsage? {
        val usage = responseNode.path("usage")
        if (usage.isMissingNode || usage.isNull) return null
        val inputTokens = usage.path("input_tokens").asInt(usage.path("prompt_tokens").asInt(-1))
        val outputTokens = usage.path("output_tokens").asInt(usage.path("completion_tokens").asInt(-1))
        val totalTokens = usage.path("total_tokens").asInt(-1)
        if (inputTokens < 0 && outputTokens < 0 && totalTokens < 0) return null
        return GrokUsage(
            prompt_tokens = inputTokens.takeIf { it >= 0 },
            completion_tokens = outputTokens.takeIf { it >= 0 },
            total_tokens = totalTokens.takeIf { it >= 0 }
        )
    }

    private fun executeFunctionTool(
        name: String,
        argumentsJson: String,
        personaId: String?
    ): Map<String, Any?> {
        return when (name) {
            "fetch_persona_metadata" -> {
                val args = parseArgs(argumentsJson)
                val requestedPersonaId = args["personaId"]?.asText()?.takeIf { it.isNotBlank() } ?: personaId
                if (requestedPersonaId.isNullOrBlank()) {
                    mapOf("ok" to false, "error" to "personaId is required")
                } else {
                    val persona = personaService.findById(requestedPersonaId)
                    if (persona == null) {
                        mapOf("ok" to false, "error" to "persona not found", "personaId" to requestedPersonaId)
                    } else {
                        mapOf(
                            "ok" to true,
                            "persona" to mapOf(
                                "id" to persona.id,
                                "name" to persona.name,
                                "description" to persona.description,
                                "webSearchEnabled" to persona.webSearchEnabled,
                                "yahooFinanceEnabled" to persona.yahooFinanceEnabled,
                                "updatedAt" to persona.updatedAt.toString()
                            )
                        )
                    }
                }
            }

            "get_exam_pool_stats" -> {
                val args = parseArgs(argumentsJson)
                val examCodeRaw = args["examCode"]?.asText()
                val examCode = examCodeRaw?.let {
                    runCatching { ExamCode.valueOf(it) }.getOrNull()
                }
                if (examCodeRaw != null && examCode == null) {
                    mapOf(
                        "ok" to false,
                        "error" to "invalid examCode",
                        "allowedValues" to ExamCode.entries.map { it.name }
                    )
                } else {
                    val perExam = (examCode?.let { listOf(it) } ?: ExamCode.entries).associate { code ->
                        code.name to coachService.getPoolSize(code)
                    }
                    mapOf("ok" to true, "examPoolStats" to perExam)
                }
            }

            else -> mapOf("ok" to false, "error" to "Unknown function: $name")
        }
    }

    private fun parseArgs(argumentsJson: String): JsonNode {
        return runCatching {
            objectMapper.readTree(argumentsJson)
        }.getOrElse {
            objectMapper.createObjectNode()
        }
    }

    private fun truncate(value: String?, maxLen: Int): String? {
        if (value == null) return null
        if (value.length <= maxLen) return value
        return value.take(maxLen) + "...(truncated)"
    }

    private fun textOrNull(node: JsonNode?): String? {
        if (node == null || node.isMissingNode || node.isNull) return null
        val value = node.asText("")
        return value.takeIf { it.isNotBlank() }
    }

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

private data class FunctionCall(
    val callId: String,
    val name: String,
    val argumentsJson: String
)