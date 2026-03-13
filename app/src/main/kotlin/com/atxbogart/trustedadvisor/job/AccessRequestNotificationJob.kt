package com.atxbogart.trustedadvisor.job

import com.atxbogart.trustedadvisor.service.AccessRequestService
import com.atxbogart.trustedadvisor.service.SlackNotificationService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class AccessRequestNotificationJob(
    private val accessRequestService: AccessRequestService,
    private val slackNotificationService: SlackNotificationService,
    @Value("\${app.slack.reminder-enabled:false}") private val reminderEnabled: Boolean
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Check for pending access requests and send a reminder to Slack.
     * Runs every 4 hours by default (configurable).
     * Only sends if there are pending requests and Slack is enabled.
     */
    @Scheduled(
        fixedRateString = "\${app.slack.reminder-interval-ms:14400000}",
        initialDelayString = "\${app.slack.reminder-initial-delay-ms:60000}"
    )
    fun checkPendingRequests() {
        if (!reminderEnabled) {
            log.debug("[job] Access request reminder disabled")
            return
        }

        if (!slackNotificationService.isEnabled()) {
            log.debug("[job] Slack notifications disabled, skipping reminder")
            return
        }

        val pendingCount = accessRequestService.getPendingRequestCount()
        if (pendingCount == 0) {
            log.debug("[job] No pending access requests")
            return
        }

        log.info("[job] Found {} pending access request(s), sending Slack reminder", pendingCount)
        slackNotificationService.sendPendingRequestsReminder(pendingCount)
    }
}
