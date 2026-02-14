package com.atxbogart.trustedadvisor.service

import com.atxbogart.trustedadvisor.model.*
import com.atxbogart.trustedadvisor.repository.CoachExamAttemptRepository
import com.atxbogart.trustedadvisor.repository.CoachExamRepository
import com.atxbogart.trustedadvisor.repository.CoachQuestionRepository
import com.atxbogart.trustedadvisor.repository.CoachUserProgressRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class CoachService(
    private val examRepository: CoachExamRepository,
    private val questionRepository: CoachQuestionRepository,
    private val progressRepository: CoachUserProgressRepository,
    private val examAttemptRepository: CoachExamAttemptRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getExams(): List<CoachExam> = examRepository.findAll()

    fun getRandomQuestion(examCode: ExamCode, excludeQuestionIds: List<String> = emptyList()): CoachQuestion? {
        val pool = questionRepository.findByExamCodeAndActiveTrue(examCode)
            .filter { it.id != null && it.id !in excludeQuestionIds }
        if (pool.isEmpty()) return null
        return pool.random()
    }

    /** Check an answer without recording progress. Returns correct, correctLetter, and explanation for UI. */
    fun checkAnswer(examCode: ExamCode, questionId: String, selectedLetter: ChoiceLetter): CheckAnswerResult? {
        val question = questionRepository.findById(questionId).orElse(null) ?: return null
        if (question.examCode != examCode) return null
        return CheckAnswerResult(
            correct = question.correctLetter == selectedLetter,
            correctLetter = question.correctLetter,
            explanation = question.explanation
        )
    }

    fun recordAnswer(userId: String, questionId: String, selectedLetter: ChoiceLetter): Boolean {
        val question = questionRepository.findById(questionId).orElse(null) ?: return false
        val isCorrect = question.correctLetter == selectedLetter

        val progress = progressRepository.findByUserIdAndExamCode(userId, question.examCode)
            ?: CoachUserProgress(userId = userId, examCode = question.examCode)

        val newWeakTopics = if (!isCorrect && question.topic != null) {
            val existing = progress.weakTopics.toMutableList()
            val idx = existing.indexOfFirst { it.topic == question.topic }
            if (idx >= 0) {
                val w = existing[idx]
                existing[idx] = w.copy(missCount = w.missCount + 1)
            } else {
                existing.add(WeakTopic(question.topic, 1))
            }
            existing
        } else progress.weakTopics

        val updated = progress.copy(
            totalAsked = progress.totalAsked + 1,
            correct = progress.correct + if (isCorrect) 1 else 0,
            lastSessionAt = LocalDateTime.now(ZoneOffset.UTC),
            weakTopics = newWeakTopics,
            updatedAt = LocalDateTime.now(ZoneOffset.UTC)
        )
        progressRepository.save(updated)
        return isCorrect
    }

    fun getProgress(userId: String, examCode: ExamCode): CoachUserProgress {
        return progressRepository.findByUserIdAndExamCode(userId, examCode)
            ?: CoachUserProgress(userId = userId, examCode = examCode)
    }

    /** Exam time limits in minutes (FINRA-style). */
    fun getExamTimeLimitMinutes(examCode: ExamCode): Int = when (examCode) {
        ExamCode.SIE -> 105       // 1 hr 45 min
        ExamCode.SERIES_7 -> 225  // 3 hr 45 min
        ExamCode.SERIES_57 -> 90  // 1 hr 30 min
    }

    /** Passing score as percentage (e.g. 70 for SIE). */
    fun getPassingPercentage(examCode: ExamCode): Int = when (examCode) {
        ExamCode.SIE -> 70
        ExamCode.SERIES_7 -> 72
        ExamCode.SERIES_57 -> 70
    }

    fun getPracticeExamQuestions(examCode: ExamCode, count: Int): PracticeSessionResponse {
        val pool = questionRepository.findByExamCodeAndActiveTrue(examCode)
            .filter { it.id != null }
        val questions = if (pool.size <= count) pool else pool.shuffled().take(count)
        val dtos = questions.map { q ->
            PracticeExamQuestion(
                id = q.id!!,
                question = q.question,
                choices = q.choices
            )
        }
        return PracticeSessionResponse(
            questions = dtos,
            totalMinutes = getExamTimeLimitMinutes(examCode)
        )
    }

    fun scorePracticeExam(examCode: ExamCode, answers: List<ScoreAnswerRequest>): ScoreResponse {
        val passingPct = getPassingPercentage(examCode)
        var correct = 0
        for (a in answers) {
            val letter = parseChoiceLetter(a.selectedLetter) ?: continue
            val question = questionRepository.findById(a.questionId).orElse(null) ?: continue
            if (question.examCode == examCode && question.correctLetter == letter) correct++
        }
        val total = answers.size
        val percentage = if (total == 0) 0.0 else (correct * 100.0) / total
        return ScoreResponse(
            correct = correct,
            total = total,
            percentage = percentage,
            passed = percentage >= passingPct,
            passingPercentage = passingPct
        )
    }

    /** Persists exam attempt and updates user progress. Call only when user completes and submits (not on cancel). */
    fun savePracticeExamResult(userId: String, examCode: ExamCode, score: ScoreResponse) {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        examAttemptRepository.save(
            CoachExamAttempt(
                userId = userId,
                examCode = examCode,
                correct = score.correct,
                total = score.total,
                percentage = score.percentage,
                passed = score.passed,
                completedAt = now,
                createdAt = now
            )
        )
        val progress = progressRepository.findByUserIdAndExamCode(userId, examCode)
            ?: CoachUserProgress(userId = userId, examCode = examCode)
        val updated = progress.copy(
            totalAsked = progress.totalAsked + score.total,
            correct = progress.correct + score.correct,
            lastSessionAt = now,
            updatedAt = now
        )
        progressRepository.save(updated)
    }

    fun getAttemptHistory(userId: String): List<CoachExamAttempt> =
        examAttemptRepository.findByUserIdOrderByCompletedAtDesc(userId)

    fun getAttemptHistoryByExam(userId: String, examCode: ExamCode): List<CoachExamAttempt> =
        examAttemptRepository.findByUserIdAndExamCodeOrderByCompletedAtDesc(userId, examCode)

    private fun parseChoiceLetter(s: String?): ChoiceLetter? = when (s?.uppercase()) {
        "A" -> ChoiceLetter.A
        "B" -> ChoiceLetter.B
        "C" -> ChoiceLetter.C
        "D" -> ChoiceLetter.D
        else -> null
    }

    @PostConstruct
    private fun seedExamsAndQuestions() {
        try {
            if (examRepository.count() > 0L) return

            val now = LocalDateTime.now(ZoneOffset.UTC)
            listOf(
                CoachExam(code = ExamCode.SIE, name = "Securities Industry Essentials", version = "2024 outline", totalQuestionsInOutline = 75, createdAt = now, updatedAt = now),
                CoachExam(code = ExamCode.SERIES_7, name = "General Securities Representative", version = "2024 outline", totalQuestionsInOutline = 125, createdAt = now, updatedAt = now),
                CoachExam(code = ExamCode.SERIES_57, name = "Securities Trader", version = "2024 outline", totalQuestionsInOutline = 50, createdAt = now, updatedAt = now)
            ).forEach { examRepository.save(it) }

            val exams = examRepository.findAll()
            if (exams.isEmpty()) return

            if (questionRepository.count() > 0L) return

            val topicsSIE = listOf("Regulatory Entities", "Securities Markets", "Customer Accounts", "Equity Securities", "Debt Securities")
            val topics7 = listOf("Options", "Municipal Securities", "Packaged Products", "Customer Recommendations", "Trading")
            val topics57 = listOf("Market Making", "Trading Rules", "Order Handling", "Regulation", "Market Structure")

            fun choice(l: ChoiceLetter, t: String) = CoachChoice(letter = l, text = t)
            fun q(examCode: ExamCode, q: String, a: String, b: String, c: String, d: String, correct: ChoiceLetter, expl: String, topic: String, diff: Difficulty) =
                CoachQuestion(
                    examCode = examCode,
                    question = q,
                    choices = listOf(choice(ChoiceLetter.A, a), choice(ChoiceLetter.B, b), choice(ChoiceLetter.C, c), choice(ChoiceLetter.D, d)),
                    correctLetter = correct,
                    explanation = expl,
                    topic = topic,
                    difficulty = diff,
                    source = "seed",
                    active = true,
                    createdAt = now,
                    updatedAt = now
                )

            val sieQs = listOf(
                q(ExamCode.SIE, "Which of the following is a self-regulatory organization (SRO)?", "SEC", "FINRA", "Congress", "State securities regulator", ChoiceLetter.B, "FINRA is an SRO that regulates broker-dealers.", topicsSIE[0], Difficulty.easy),
                q(ExamCode.SIE, "The SEC was established by which act?", "Securities Act of 1933", "Securities Exchange Act of 1934", "Investment Company Act of 1940", "Investment Advisers Act of 1940", ChoiceLetter.B, "The Exchange Act of 1934 created the SEC.", topicsSIE[0], Difficulty.medium),
                q(ExamCode.SIE, "A primary market transaction involves:", "Trading between investors", "Issuer selling new securities to investors", "Market maker inventory", "Secondary offering only", ChoiceLetter.B, "Primary market is when the issuer sells new securities.", topicsSIE[1], Difficulty.easy),
                q(ExamCode.SIE, "Which account requires a signed customer agreement?", "Cash account", "Margin account", "Both require it", "Neither", ChoiceLetter.C, "Both cash and margin accounts require a signed agreement.", topicsSIE[2], Difficulty.easy),
                q(ExamCode.SIE, "Common stock represents:", "Debt of the issuer", "Ownership in the company", "A fixed dividend", "Priority in liquidation", ChoiceLetter.B, "Common stock represents equity ownership.", topicsSIE[3], Difficulty.easy),
                q(ExamCode.SIE, "A bond's yield to maturity assumes:", "All coupons are spent", "Bond is held to maturity", "Interest rates are unchanged", "Default does not occur", ChoiceLetter.B, "YTM assumes the bond is held until maturity.", topicsSIE[4], Difficulty.medium),
                q(ExamCode.SIE, "FINRA membership is required for:", "Investment advisers only", "Broker-dealers engaged in securities business", "Hedge funds", "Banks only", ChoiceLetter.B, "Broker-dealers must be FINRA members.", topicsSIE[0], Difficulty.easy),
                q(ExamCode.SIE, "Blue sky laws refer to:", "Federal registration", "State securities registration", "SEC rules", "FINRA rules", ChoiceLetter.B, "State securities laws are called blue sky laws.", topicsSIE[0], Difficulty.medium),
                q(ExamCode.SIE, "In a margin account, the customer must maintain:", "No minimum", "Initial margin only", "Minimum maintenance margin", "Only Reg T margin", ChoiceLetter.C, "Maintenance margin must be maintained after initial margin.", topicsSIE[2], Difficulty.medium),
                q(ExamCode.SIE, "Preferred stock typically has:", "Voting rights equal to common", "No voting rights", "Priority over common in dividends", "Both B and C", ChoiceLetter.D, "Preferred usually has dividend priority and often no voting rights.", topicsSIE[3], Difficulty.easy),
                q(ExamCode.SIE, "Municipal bonds are issued by:", "The federal government", "State and local governments", "Corporations", "Banks", ChoiceLetter.B, "Municipals are issued by state and local governments.", topicsSIE[4], Difficulty.easy),
                q(ExamCode.SIE, "The MSRB regulates:", "Mutual funds", "Municipal securities dealers", "Options exchanges", "Futures", ChoiceLetter.B, "MSRB regulates municipal securities dealers.", topicsSIE[0], Difficulty.medium),
                q(ExamCode.SIE, "A customer's signature on a new account form is required within:", "30 days", "60 days", "No specific period", "Before first trade", ChoiceLetter.A, "NASD/FINRA rules require signature within 30 days.", topicsSIE[2], Difficulty.hard),
                q(ExamCode.SIE, "Which is true of the OTC market?", "Only listed securities", "Dealer market", "Auction market only", "No NASDAQ", ChoiceLetter.B, "OTC is a dealer market.", topicsSIE[1], Difficulty.easy),
                q(ExamCode.SIE, "Treasury bonds have:", "Credit risk only", "Interest rate risk", "No risk", "Only inflation risk", ChoiceLetter.B, "Treasuries have interest rate risk.", topicsSIE[4], Difficulty.easy),
                q(ExamCode.SIE, "An accredited investor includes someone with income over:", "$100,000", "$200,000 (or $300,000 joint)", "$500,000", "$1,000,000", ChoiceLetter.B, "Accredited: $200K individual or $300K joint income.", topicsSIE[2], Difficulty.medium),
                q(ExamCode.SIE, "The Securities Act of 1933 primarily governs:", "Secondary market trading", "Registration of new offerings", "Broker conduct", "Margin rules", ChoiceLetter.B, "1933 Act governs registration of new securities.", topicsSIE[0], Difficulty.easy),
                q(ExamCode.SIE, "A prospectus must be delivered:", "Only for mutual funds", "In connection with a new offering", "Only for IPOs", "Never for bonds", ChoiceLetter.B, "Prospectus is required in connection with new offerings.", topicsSIE[1], Difficulty.medium),
                q(ExamCode.SIE, "Equity securities include:", "Corporate bonds", "Treasury notes", "Common and preferred stock", "Municipal bonds", ChoiceLetter.C, "Equity = common and preferred stock.", topicsSIE[3], Difficulty.easy),
                q(ExamCode.SIE, "SIPC protects customers against:", "Market loss", "Broker-dealer insolvency", "Fraud", "Recommendation loss", ChoiceLetter.B, "SIPC protects against broker insolvency, not market loss.", topicsSIE[2], Difficulty.medium)
            )
            sieQs.forEach { questionRepository.save(it) }

            val series7Qs = listOf(
                q(ExamCode.SERIES_7, "A long call option gives the holder the:", "Obligation to buy", "Right to buy", "Obligation to sell", "Right to sell", ChoiceLetter.B, "Long call = right to buy the underlying.", topics7[0], Difficulty.easy),
                q(ExamCode.SERIES_7, "A customer sells a naked put. Maximum gain is:", "Unlimited", "Strike price minus premium", "Premium received", "Strike price plus premium", ChoiceLetter.C, "Max gain on short put = premium received.", topics7[0], Difficulty.medium),
                q(ExamCode.SERIES_7, "General obligation bonds are backed by:", "Revenue of a project", "Full faith and credit of the issuer", "Federal guarantee", "Private insurance", ChoiceLetter.B, "GO bonds are backed by taxing power.", topics7[1], Difficulty.easy),
                q(ExamCode.SERIES_7, "A unit investment trust:", "Has an active manager", "Has a fixed portfolio", "Trades at NAV only", "Is always a closed-end fund", ChoiceLetter.B, "UIT has a fixed, unmanaged portfolio.", topics7[2], Difficulty.medium),
                q(ExamCode.SERIES_7, "Suitability requires the rep to:", "Guarantee returns", "Recommend only what is suitable for the customer", "Avoid all risk", "Use only internal research", ChoiceLetter.B, "Suitability = recommend suitable investments for the customer.", topics7[3], Difficulty.easy),
                q(ExamCode.SERIES_7, "Best execution means:", "Lowest commission only", "Best overall terms for the customer", "Fastest execution only", "Only price", ChoiceLetter.B, "Best execution = best overall terms.", topics7[4], Difficulty.easy),
                q(ExamCode.SERIES_7, "When may a rep share in a customer's account?", "Never", "With written authorization and firm approval", "Only in margin accounts", "Only for institutional accounts", ChoiceLetter.B, "Sharing requires written authorization and firm approval.", topics7[3], Difficulty.medium),
                q(ExamCode.SERIES_7, "Revenue bonds are backed by:", "Taxing power", "Project revenue", "Federal guarantee", "State guarantee", ChoiceLetter.B, "Revenue bonds are backed by project revenue.", topics7[1], Difficulty.easy),
                q(ExamCode.SERIES_7, "A spread is the difference between:", "Bid and ask", "High and low", "Open and close", "Volume and open interest", ChoiceLetter.A, "Spread = bid-ask difference.", topics7[4], Difficulty.easy),
                q(ExamCode.SERIES_7, "An open-end fund:", "Trades on an exchange", "Redeems shares at NAV", "Has a fixed number of shares", "Is a UIT", ChoiceLetter.B, "Open-end funds redeem at NAV.", topics7[2], Difficulty.easy),
                q(ExamCode.SERIES_7, "Writing a covered call means the writer:", "Has no position in the stock", "Owns the underlying stock", "Is short the stock", "Has a long call", ChoiceLetter.B, "Covered call = short call + long stock.", topics7[0], Difficulty.medium),
                q(ExamCode.SERIES_7, "Breakpoint sales involve:", "Selling at a loss", "Volume discounts on mutual fund purchases", "Bond yields", "Option strike prices", ChoiceLetter.B, "Breakpoints are volume discounts on mutual funds.", topics7[2], Difficulty.medium),
                q(ExamCode.SERIES_7, "Churning refers to:", "Excessive trading for commissions", "Selling at a loss", "Unsuitable recommendations", "Front-running", ChoiceLetter.A, "Churning = excessive trading for commissions.", topics7[3], Difficulty.easy),
                q(ExamCode.SERIES_7, "A limit order:", "Executes at market price", "Executes at or better than specified price", "Is good for the day only", "Cannot be cancelled", ChoiceLetter.B, "Limit order = at or better than limit price.", topics7[4], Difficulty.easy),
                q(ExamCode.SERIES_7, "ADR represents:", "A domestic bond", "Receipt for foreign shares", "A mutual fund share", "A Treasury", ChoiceLetter.B, "ADR = American Depositary Receipt for foreign shares.", topics7[3], Difficulty.easy),
                q(ExamCode.SERIES_7, "Delta measures an option's sensitivity to:", "Time decay", "Underlying price change", "Volatility only", "Interest rates only", ChoiceLetter.B, "Delta = sensitivity to underlying price.", topics7[0], Difficulty.medium),
                q(ExamCode.SERIES_7, "A prospectus is required to be delivered:", "Only at sale", "Before or at sale", "Within 30 days after", "Only for IPOs", ChoiceLetter.B, "Prospectus must be delivered before or at sale.", topics7[2], Difficulty.easy),
                q(ExamCode.SERIES_7, "Markup on a principal transaction must be:", "Disclosed only if asked", "Reasonable", "Zero", "Only on bonds", ChoiceLetter.B, "Markups must be reasonable.", topics7[4], Difficulty.medium),
                q(ExamCode.SERIES_7, "A customer wants growth and can accept risk. Best recommendation is:", "Money market fund", "Growth equity fund", "Treasury bills", "CD", ChoiceLetter.B, "Growth with risk tolerance suggests equity.", topics7[3], Difficulty.easy),
                q(ExamCode.SERIES_7, "Settlement for regular way equity is:", "T+0", "T+1", "T+2", "T+3", ChoiceLetter.C, "Regular way equity settles T+2.", topics7[4], Difficulty.medium)
            )
            series7Qs.forEach { questionRepository.save(it) }

            val series57Qs = listOf(
                q(ExamCode.SERIES_57, "A market maker quotes 10.00 - 10.05. The spread is:", "5 cents", "10 cents", "0", "10.05", ChoiceLetter.A, "Spread = ask - bid = 10.05 - 10.00 = 5 cents.", topics57[0], Difficulty.easy),
                q(ExamCode.SERIES_57, "Locked market occurs when:", "Bid equals ask", "No spread", "Both A and B", "Bid exceeds ask", ChoiceLetter.C, "Locked market = bid = ask (no spread).", topics57[0], Difficulty.medium),
                q(ExamCode.SERIES_57, "Which order has the highest priority?", "Price", "Time", "Size", "Display", ChoiceLetter.A, "Price priority is first, then time.", topics57[2], Difficulty.easy),
                q(ExamCode.SERIES_57, "Trade-through rules are designed to:", "Speed up execution", "Protect displayed quotes", "Reduce spreads", "Allow dark pools only", ChoiceLetter.B, "Trade-through rules protect displayed liquidity.", topics57[3], Difficulty.medium),
                q(ExamCode.SERIES_57, "A market order guarantees:", "Price", "Execution", "Size", "Time", ChoiceLetter.B, "Market order guarantees execution, not price.", topics57[2], Difficulty.easy),
                q(ExamCode.SERIES_57, "Reg NMS applies to:", "Options only", "Equity securities", "Bonds only", "Futures", ChoiceLetter.B, "Reg NMS applies to listed equity securities.", topics57[3], Difficulty.easy),
                q(ExamCode.SERIES_57, "Odd lot is fewer than:", "100 shares", "10 shares", "1 round lot", "500 shares", ChoiceLetter.A, "Odd lot = less than 100 shares.", topics57[1], Difficulty.easy),
                q(ExamCode.SERIES_57, "Best execution applies to:", "Only retail", "Only institutional", "All customer orders", "Only market orders", ChoiceLetter.C, "Best execution applies to all customer orders.", topics57[2], Difficulty.easy),
                q(ExamCode.SERIES_57, "A dealer holds inventory in:", "Agency market", "Principal capacity", "Auction only", "Cross only", ChoiceLetter.B, "Dealer holds inventory as principal.", topics57[0], Difficulty.easy),
                q(ExamCode.SERIES_57, "Displayed liquidity is protected under:", "Reg ATS", "Reg NMS order protection", "Reg SHO only", "No rule", ChoiceLetter.B, "Reg NMS order protection rule protects displayed quotes.", topics57[3], Difficulty.medium),
                q(ExamCode.SERIES_57, "Short selling requires:", "No locate", "Locate for borrow", "Only for market makers", "Only for institutional", ChoiceLetter.B, "Short sales require a locate for borrow.", topics57[1], Difficulty.medium),
                q(ExamCode.SERIES_57, "Tick size for stocks under $1 may be:", "0.01", "0.001", "0.0001", "Same as over $1", ChoiceLetter.B, "Sub-penny pricing allowed for stocks under $1 in some cases.", topics57[4], Difficulty.hard),
                q(ExamCode.SERIES_57, "Market structure includes:", "Exchanges and ATSs", "Only NYSE", "Only NASDAQ", "Only dark pools", ChoiceLetter.A, "Market structure includes exchanges and ATSs.", topics57[4], Difficulty.easy),
                q(ExamCode.SERIES_57, "An ATS is:", "An exchange", "An alternative trading system", "A clearing house", "A regulator", ChoiceLetter.B, "ATS = alternative trading system.", topics57[4], Difficulty.easy),
                q(ExamCode.SERIES_57, "Price improvement means execution:", "At a worse price", "Between bid and ask", "At the quote", "Only for retail", ChoiceLetter.B, "Price improvement = better than quoted price.", topics57[2], Difficulty.easy),
                q(ExamCode.SERIES_57, "Two-sided quote means:", "Bid only", "Ask only", "Both bid and ask", "No quote", ChoiceLetter.C, "Two-sided = both bid and ask displayed.", topics57[0], Difficulty.easy),
                q(ExamCode.SERIES_57, "Order handling rules require:", "Display of limit orders", "No display", "Display only for retail", "Display only on exchange", ChoiceLetter.A, "Limit orders must be displayed per rules.", topics57[3], Difficulty.medium),
                q(ExamCode.SERIES_57, "Consolidated tape shows:", "Trades from all markets", "Only NYSE", "Only NASDAQ", "Only one exchange", ChoiceLetter.A, "Consolidated tape = all markets.", topics57[4], Difficulty.easy),
                q(ExamCode.SERIES_57, "A block trade is typically:", "Under 10,000 shares", "Over 10,000 shares", "Exactly 100 shares", "Odd lot", ChoiceLetter.B, "Block = large trade, often 10,000+ shares.", topics57[1], Difficulty.easy),
                q(ExamCode.SERIES_57, "Market maker capital is used for:", "Only agency", "Inventory and risk", "Only clearing", "Only routing", ChoiceLetter.B, "Market makers use capital for inventory and risk.", topics57[0], Difficulty.medium)
            )
            series57Qs.forEach { questionRepository.save(it) }

            log.info("Coach: seeded 3 exams and 60 sample questions.")
        } catch (e: Exception) {
            log.warn("Could not seed coach data (Mongo may be down): {}", e.message)
        }
    }
}
