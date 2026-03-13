package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.AccessRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

data class SlackMessage(
    val text: String? = null,
    val blocks: List<SlackBlock>? = null
)

data class SlackBlock(
    val type: String,
    val text: SlackText? = null,
    val elements: List<SlackElement>? = null,
    val accessory: SlackAccessory? = null
)

data class SlackText(
    val type: String,
    val text: String,
    val emoji: Boolean? = null
)

data class SlackElement(
    val type: String,
    val text: Any? = null,
    val url: String? = null,
    val style: String? = null,
    val action_id: String? = null
)

data class SlackAccessory(
    val type: String,
    val image_url: String? = null,
    val alt_text: String? = null
)

sealed class NotificationResult {
    data object Success : NotificationResult()
    data object Disabled : NotificationResult()
    data class Error(val message: String) : NotificationResult()
}

@Service
class SlackNotificationService(
    @Value("\${app.slack.webhook-url:}") private val webhookUrl: String,
    @Value("\${app.slack.enabled:false}") private val enabled: Boolean,
    @Value("\${app.frontend-url:http://localhost:3000}") private val frontendUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()
    private val objectMapper = ObjectMapper()

    fun isEnabled(): Boolean = enabled && webhookUrl.isNotBlank()

    fun sendAccessRequestNotification(request: AccessRequest): NotificationResult {
        if (!isEnabled()) {
            log.debug("[slack] Notifications disabled, skipping access request notification")
            return NotificationResult.Disabled
        }

        val message = buildAccessRequestMessage(request)
        return sendMessage(message)
    }

    fun sendAccessApprovedNotification(request: AccessRequest, approvedBy: String): NotificationResult {
        if (!isEnabled()) return NotificationResult.Disabled

        val message = SlackMessage(
            blocks = listOf(
                SlackBlock(
                    type = "header",
                    text = SlackText(type = "plain_text", text = "✅ Access Request Approved", emoji = true)
                ),
                SlackBlock(
                    type = "section",
                    text = SlackText(
                        type = "mrkdwn",
                        text = "*${request.email}* has been approved by *${approvedBy}*"
                    )
                )
            )
        )
        return sendMessage(message)
    }

    fun sendAccessRejectedNotification(request: AccessRequest, rejectedBy: String): NotificationResult {
        if (!isEnabled()) return NotificationResult.Disabled

        val message = SlackMessage(
            blocks = listOf(
                SlackBlock(
                    type = "header",
                    text = SlackText(type = "plain_text", text = "❌ Access Request Rejected", emoji = true)
                ),
                SlackBlock(
                    type = "section",
                    text = SlackText(
                        type = "mrkdwn",
                        text = "*${request.email}* was rejected by *${rejectedBy}*"
                    )
                )
            )
        )
        return sendMessage(message)
    }

    fun sendPendingRequestsReminder(count: Int): NotificationResult {
        if (!isEnabled()) return NotificationResult.Disabled
        if (count == 0) return NotificationResult.Success

        val message = SlackMessage(
            blocks = listOf(
                SlackBlock(
                    type = "header",
                    text = SlackText(type = "plain_text", text = "📋 Pending Access Requests", emoji = true)
                ),
                SlackBlock(
                    type = "section",
                    text = SlackText(
                        type = "mrkdwn",
                        text = "There ${if (count == 1) "is *1* pending request" else "are *$count* pending requests"} awaiting review."
                    )
                ),
                SlackBlock(
                    type = "actions",
                    elements = listOf(
                        SlackElement(
                            type = "button",
                            text = SlackText(type = "plain_text", text = "Review Requests", emoji = true),
                            url = "$frontendUrl/admin/access-requests",
                            style = "primary",
                            action_id = "review_requests"
                        )
                    )
                )
            )
        )
        return sendMessage(message)
    }

    private fun buildAccessRequestMessage(request: AccessRequest): SlackMessage {
        val blocks = mutableListOf<SlackBlock>()

        blocks.add(
            SlackBlock(
                type = "header",
                text = SlackText(type = "plain_text", text = "🔔 New Access Request", emoji = true)
            )
        )

        val infoText = buildString {
            append("*Email:* ${request.email}\n")
            request.displayName?.let { append("*Name:* $it\n") }
            request.oauthProvider?.let { append("*Provider:* $it\n") }
            request.reason?.let { append("*Reason:* $it\n") }
        }

        blocks.add(
            SlackBlock(
                type = "section",
                text = SlackText(type = "mrkdwn", text = infoText),
                accessory = request.profileImageUrl?.let {
                    SlackAccessory(
                        type = "image",
                        image_url = it,
                        alt_text = request.displayName ?: request.email
                    )
                }
            )
        )

        blocks.add(
            SlackBlock(
                type = "actions",
                elements = listOf(
                    SlackElement(
                        type = "button",
                        text = SlackText(type = "plain_text", text = "Review in Admin", emoji = true),
                        url = "$frontendUrl/admin/access-requests",
                        style = "primary",
                        action_id = "review_access_request"
                    )
                )
            )
        )

        blocks.add(
            SlackBlock(
                type = "context",
                elements = listOf(
                    SlackElement(
                        type = "mrkdwn",
                        text = SlackText(type = "mrkdwn", text = "Requested at ${request.createdAt}")
                    )
                )
            )
        )

        return SlackMessage(blocks = blocks)
    }

    private fun sendMessage(message: SlackMessage): NotificationResult {
        return try {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val payload = objectMapper.writeValueAsString(message)
            val entity = HttpEntity(payload, headers)

            val response = restTemplate.postForEntity(webhookUrl, entity, String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                log.info("[slack] Notification sent successfully")
                NotificationResult.Success
            } else {
                log.warn("[slack] Notification failed with status: {}", response.statusCode)
                NotificationResult.Error("HTTP ${response.statusCode}")
            }
        } catch (e: Exception) {
            log.error("[slack] Failed to send notification: {}", e.message)
            NotificationResult.Error(e.message ?: "Unknown error")
        }
    }
}
