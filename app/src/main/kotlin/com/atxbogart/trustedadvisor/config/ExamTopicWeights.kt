package com.atxbogart.trustedadvisor.config

import com.atxbogart.trustedadvisor.model.ExamCode

/**
 * FINRA-aligned topic weights per exam (percentage of questions by topic).
 * Used to build practice exams that match the real exam distribution.
 * @see https://www.finra.org/industry/series7
 * @see https://www.finra.org/registration-exams-ce/qualification-exams/series57
 * SIE: Knowledge of Capital Markets 16%, Products and Risks 44%, Trading/Customer Accounts 31%, Regulatory Framework 9%.
 * Series 7: Function 1 (Seeks Business) 7%, F2 (Opens Accounts) 9%, F3 (Information/Recommendations) 73%, F4 (Processes Transactions) 11%.
 * Series 57: Trading Activities 82%, Books and Records/Trade Reporting 18%.
 * Series 65: NASAA outline topics (simplified equal-ish distribution).
 */
object ExamTopicWeights {

    data class TopicWeight(val topic: String, val weightPercent: Int)

    fun topicsForExam(examCode: ExamCode): List<TopicWeight> = when (examCode) {
        ExamCode.SIE -> listOf(
            TopicWeight("Knowledge of Capital Markets", 16),
            TopicWeight("Understanding Products and Their Risks", 44),
            TopicWeight("Understanding Trading, Customer Accounts and Prohibited Activities", 31),
            TopicWeight("Overview of the Regulatory Framework", 9)
        )
        ExamCode.SERIES_7 -> listOf(
            TopicWeight("Seeks Business for the Broker-Dealer", 7),
            TopicWeight("Opens Accounts and Evaluates Financial Profile", 9),
            TopicWeight("Provides Information, Recommendations, Transfers Assets", 73),
            TopicWeight("Obtains and Verifies Instructions; Processes Transactions", 11)
        )
        ExamCode.SERIES_57 -> listOf(
            TopicWeight("Trading Activities", 82),
            TopicWeight("Books and Records, Trade Reporting and Settlement", 18)
        )
        ExamCode.SERIES_65 -> listOf(
            TopicWeight("Regulations and Laws", 22),
            TopicWeight("Ethics and Fiduciary Duty", 20),
            TopicWeight("Investment Vehicles", 22),
            TopicWeight("Client Strategies and Economic Factors", 20),
            TopicWeight("Communications and Documentation", 16)
        )
    }

    /** Topic names only (for prompts). */
    fun topicNamesForExam(examCode: ExamCode): List<String> =
        topicsForExam(examCode).map { it.topic }

    /**
     * For a given total question count, returns the target count per topic (may sum to total or total-1 due to rounding).
     */
    fun targetCountsByTopic(examCode: ExamCode, totalCount: Int): Map<String, Int> {
        val weights = topicsForExam(examCode)
        val sumPercent = weights.sumOf { it.weightPercent }
        if (sumPercent == 0) return emptyMap()
        val assigned = weights.map { tw ->
            tw.topic to (totalCount * tw.weightPercent / sumPercent).coerceAtLeast(0)
        }.toMap().toMutableMap()
        val currentTotal = assigned.values.sum()
        if (currentTotal < totalCount && weights.isNotEmpty()) {
            val firstTopic = weights.first().topic
            assigned[firstTopic] = (assigned[firstTopic] ?: 0) + (totalCount - currentTotal)
        }
        return assigned
    }
}
