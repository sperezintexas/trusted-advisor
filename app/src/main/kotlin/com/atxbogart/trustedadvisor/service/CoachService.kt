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
        ExamCode.SERIES_65 -> 180 // 3 hr (130 questions)
    }

    /** Passing score as percentage (e.g. 70 for SIE). */
    fun getPassingPercentage(examCode: ExamCode): Int = when (examCode) {
        ExamCode.SIE -> 70
        ExamCode.SERIES_7 -> 72
        ExamCode.SERIES_57 -> 70
        ExamCode.SERIES_65 -> 73
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
            val now = LocalDateTime.now(ZoneOffset.UTC)
            val examsToSeed = listOf(
                CoachExam(code = ExamCode.SIE, name = "Securities Industry Essentials", version = "2024 outline", totalQuestionsInOutline = 75, createdAt = now, updatedAt = now),
                CoachExam(code = ExamCode.SERIES_7, name = "General Securities Representative", version = "2024 outline", totalQuestionsInOutline = 125, createdAt = now, updatedAt = now),
                CoachExam(code = ExamCode.SERIES_57, name = "Securities Trader", version = "2024 outline", totalQuestionsInOutline = 50, createdAt = now, updatedAt = now),
                CoachExam(code = ExamCode.SERIES_65, name = "Uniform Investment Adviser Law", version = "NASAA outline", totalQuestionsInOutline = 130, createdAt = now, updatedAt = now)
            )
            examsToSeed.forEach { exam ->
                if (examRepository.findByCode(exam.code) == null) {
                    examRepository.save(exam)
                }
            }

            val topicsSIE = listOf("Regulatory Entities", "Securities Markets", "Customer Accounts", "Equity Securities", "Debt Securities")
            val topics7 = listOf("Options", "Municipal Securities", "Packaged Products", "Customer Recommendations", "Trading")
            val topics57 = listOf("Market Making", "Trading Rules", "Order Handling", "Regulation", "Market Structure")
            val topics65 = listOf("Regulations and Laws", "Ethics and Fiduciary Duty", "Investment Vehicles", "Client Strategies", "Economic Factors")

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
            if (questionRepository.findByExamCodeAndActiveTrue(ExamCode.SIE).isEmpty()) {
                sieQs.forEach { questionRepository.save(it) }
            }

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
            if (questionRepository.findByExamCodeAndActiveTrue(ExamCode.SERIES_7).isEmpty()) {
                series7Qs.forEach { questionRepository.save(it) }
            }

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
            if (questionRepository.findByExamCodeAndActiveTrue(ExamCode.SERIES_57).isEmpty()) {
                series57Qs.forEach { questionRepository.save(it) }
            }

            val series65Qs = listOf(
                q(ExamCode.SERIES_65, "The Investment Advisers Act of 1940 is primarily enforced by:", "FINRA", "SEC", "State regulators only", "NASAA", ChoiceLetter.B, "The SEC enforces the federal Investment Advisers Act of 1940.", topics65[0], Difficulty.easy),
                q(ExamCode.SERIES_65, "An investment adviser is defined as someone who:", "Sells securities for commission", "Gives advice about securities for compensation", "Only manages mutual funds", "Only advises institutions", ChoiceLetter.B, "IA = gives advice about securities for compensation.", topics65[0], Difficulty.easy),
                q(ExamCode.SERIES_65, "Which is typically exempt from federal IA registration?", "Adviser with $110M AUM", "Adviser to investment companies only", "Adviser with only insurance products", "Adviser with only one client", ChoiceLetter.C, "Advisers solely to insurance products may be exempt.", topics65[0], Difficulty.medium),
                q(ExamCode.SERIES_65, "State-registered investment advisers are regulated by:", "SEC only", "State securities authorities", "FINRA", "MSRB", ChoiceLetter.B, "State-registered IAs are regulated by state securities authorities.", topics65[0], Difficulty.easy),
                q(ExamCode.SERIES_65, "The brochure rule requires delivery of:", "A summary of the advisory agreement", "Form ADV Part 2 (or equivalent)", "Only Part 1", "Only at client request", ChoiceLetter.B, "Part 2 (brochure) must be delivered to clients.", topics65[0], Difficulty.medium),
                q(ExamCode.SERIES_65, "Custody under the IA rule generally means:", "Having authority to withdraw client funds", "Only holding client securities", "Having power of attorney", "Only for pooled vehicles", ChoiceLetter.A, "Custody = authority to withdraw or possess client funds/securities.", topics65[0], Difficulty.medium),
                q(ExamCode.SERIES_65, "A federal covered adviser is one that:", "Is registered only with the SEC", "Is exempt from state registration", "Is registered in all states", "Is only state-registered", ChoiceLetter.B, "Federal covered = registered with SEC, generally not state.", topics65[0], Difficulty.medium),
                q(ExamCode.SERIES_65, "The de minimis exemption allows state registration when:", "AUM exceeds $110M", "Fewer than 6 clients in the state", "Only 5 or fewer clients total", "No place of business in state", ChoiceLetter.D, "De minimis: no place of business in state, limited clients there.", topics65[0], Difficulty.hard),
                q(ExamCode.SERIES_65, "An IAR is:", "Investment Adviser Representative", "Independent Audit Report", "Internal Assessment Review", "Investment Allocation Ratio", ChoiceLetter.A, "IAR = Investment Adviser Representative.", topics65[0], Difficulty.easy),
                q(ExamCode.SERIES_65, "Form ADV is filed with:", "FINRA", "SEC and/or state authorities", "NASAA only", "MSRB", ChoiceLetter.B, "Form ADV is filed with SEC and/or state regulators.", topics65[0], Difficulty.easy),
                q(ExamCode.SERIES_65, "The antifraud provisions of the IA Act apply to:", "Only SEC-registered advisers", "All investment advisers", "Only those with custody", "Only those with AUM over $100M", ChoiceLetter.B, "Antifraud provisions apply to all IAs regardless of registration.", topics65[0], Difficulty.medium),
                q(ExamCode.SERIES_65, "A wrap fee program typically includes:", "Only trading commissions", "Advisory fee and execution in one fee", "Only custody fees", "Only performance fees", ChoiceLetter.B, "Wrap fee = bundled advisory and execution.", topics65[0], Difficulty.medium),
                q(ExamCode.SERIES_65, "Performance-based fees are generally permitted for:", "Only mutual funds", "Qualified clients under SEC rules", "Any client", "Only institutional", ChoiceLetter.B, "Performance fees allowed for qualified clients per SEC rules.", topics65[0], Difficulty.medium),
                q(ExamCode.SERIES_65, "The Investment Company Act of 1940 governs:", "All investment advisers", "Mutual funds and similar companies", "Broker-dealers", "Hedge funds only", ChoiceLetter.B, "1940 Act governs investment companies (e.g. mutual funds).", topics65[0], Difficulty.easy),
                q(ExamCode.SERIES_65, "NASAA is:", "A federal agency", "An association of state securities regulators", "A self-regulatory organization", "A branch of the SEC", ChoiceLetter.B, "NASAA = North American Securities Administrators Association.", topics65[0], Difficulty.easy),
                q(ExamCode.SERIES_65, "Registration as an IAR typically requires:", "Only firm registration", "Passing Series 65 or 66 (or equivalent)", "Only state approval", "Only SEC approval", ChoiceLetter.B, "IARs generally must pass Series 65/66 or equivalent.", topics65[0], Difficulty.easy),
                q(ExamCode.SERIES_65, "An adviser with a principal place of business in a state must register in:", "Only the SEC", "That state", "All states where clients reside", "No state", ChoiceLetter.B, "Adviser registers in state where principal place of business is.", topics65[0], Difficulty.medium),
                q(ExamCode.SERIES_65, "The Uniform Securities Act is a:", "Federal law", "Model state law", "FINRA rule", "SEC regulation", ChoiceLetter.B, "USA is a model state law adopted by many states.", topics65[0], Difficulty.medium),
                q(ExamCode.SERIES_65, "Fraud under securities law includes:", "Only misrepresentation", "Misrepresentation, omission of material fact, and manipulative acts", "Only insider trading", "Only churning", ChoiceLetter.B, "Fraud includes misrepresentation, material omissions, manipulation.", topics65[0], Difficulty.medium),
                q(ExamCode.SERIES_65, "An IA must maintain books and records for:", "1 year", "3 years", "5 years (some longer)", "10 years", ChoiceLetter.C, "IAs must maintain books and records for at least 5 years.", topics65[0], Difficulty.medium),
                q(ExamCode.SERIES_65, "A fiduciary duty means the adviser must act in:", "The adviser's best interest", "The client's best interest", "The firm's best interest", "The custodian's best interest", ChoiceLetter.B, "Fiduciary duty = act in the client's best interest.", topics65[1], Difficulty.easy),
                q(ExamCode.SERIES_65, "Disclosure of conflicts of interest should be:", "Only if asked", "In writing, before or at the time of the advice", "Only verbal", "Only in the annual report", ChoiceLetter.B, "Material conflicts must be disclosed in writing in advance.", topics65[1], Difficulty.easy),
                q(ExamCode.SERIES_65, "Suitability for an IA means:", "Recommendations must be suitable for the client", "Only execution must be suitable", "Only for wrap accounts", "Only for institutional clients", ChoiceLetter.A, "IA recommendations must be suitable for the client.", topics65[1], Difficulty.easy),
                q(ExamCode.SERIES_65, "Churning in an advisory account refers to:", "Rebalancing", "Excessive trading to generate fees", "Selling losers", "Diversification", ChoiceLetter.B, "Churning = excessive trading to generate fees.", topics65[1], Difficulty.easy),
                q(ExamCode.SERIES_65, "An IA may receive third-party compensation if:", "Never", "Disclosed and client consents", "Only from the custodian", "Only for referrals", ChoiceLetter.B, "Third-party compensation must be disclosed and client may consent.", topics65[1], Difficulty.medium),
                q(ExamCode.SERIES_65, "Best execution for an IA means:", "Lowest commission only", "Best overall terms for the client", "Fastest execution only", "Only when directed", ChoiceLetter.B, "Best execution = best overall terms for the client.", topics65[1], Difficulty.easy),
                q(ExamCode.SERIES_65, "Soft dollar arrangements involve:", "Cash rebates to the client", "Using client commission to obtain research/services", "Hard currency only", "No disclosure required", ChoiceLetter.B, "Soft dollars = client commissions used for research/services.", topics65[1], Difficulty.medium),
                q(ExamCode.SERIES_65, "A code of ethics under the IA rule typically requires:", "Only compliance manual", "Reporting of personal trading and adherence to standards", "Only annual review", "Only for large firms", ChoiceLetter.B, "Code of ethics includes personal trading and standards.", topics65[1], Difficulty.medium),
                q(ExamCode.SERIES_65, "Front-running is:", "Trading ahead of client orders to benefit the firm", "Selling before the client", "Only for market makers", "Permitted with disclosure", ChoiceLetter.A, "Front-running = trading ahead of client to benefit from the move.", topics65[1], Difficulty.medium),
                q(ExamCode.SERIES_65, "Material non-public information:", "May be used with disclosure", "Must not be used for trading", "May be shared with other clients", "Is only insider trading if disclosed", ChoiceLetter.B, "Use of material non-public information is illegal.", topics65[1], Difficulty.easy),
                q(ExamCode.SERIES_65, "An IA's fiduciary duty applies:", "Only to discretionary accounts", "To all advisory relationships", "Only when charging a fee", "Only to retail clients", ChoiceLetter.B, "Fiduciary duty applies to all advisory relationships.", topics65[1], Difficulty.easy),
                q(ExamCode.SERIES_65, "Disclosure of fees must be:", "Only in the agreement", "Clear and in writing, before engagement", "Only annually", "Only if over 1%", ChoiceLetter.B, "Fees must be clearly disclosed in writing before engagement.", topics65[1], Difficulty.easy),
                q(ExamCode.SERIES_65, "Recommending a security in which the IA has an interest requires:", "No disclosure", "Disclosure of the conflict", "Only verbal disclosure", "Only if asked", ChoiceLetter.B, "Conflict of interest must be disclosed.", topics65[1], Difficulty.easy),
                q(ExamCode.SERIES_65, "Custody rule requires, among other things:", "Annual surprise examination or audit", "No specific requirement", "Only quarterly statements", "Only for mutual funds", ChoiceLetter.A, "Custody rule requires surprise exam or audit for qualified custody.", topics65[1], Difficulty.medium),
                q(ExamCode.SERIES_65, "Suitability considerations include:", "Only risk tolerance", "Client's financial situation, investment objectives, risk tolerance", "Only time horizon", "Only age", ChoiceLetter.B, "Suitability considers situation, objectives, risk, and more.", topics65[1], Difficulty.easy),
                q(ExamCode.SERIES_65, "An IA that recommends only proprietary products:", "Has no conflict", "Has a conflict that must be disclosed", "Need not disclose", "Only if fee-based", ChoiceLetter.B, "Proprietary product recommendations create a conflict to disclose.", topics65[1], Difficulty.medium),
                q(ExamCode.SERIES_65, "Rebalancing a portfolio:", "Is always churning", "Can be appropriate to maintain strategy", "Requires no disclosure", "Is prohibited", ChoiceLetter.B, "Rebalancing can be appropriate to maintain allocation.", topics65[1], Difficulty.easy),
                q(ExamCode.SERIES_65, "Bonds are generally:", "Equity securities", "Debt instruments", "Derivatives", "Commodities", ChoiceLetter.B, "Bonds are debt instruments.", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "When interest rates rise, existing bond prices typically:", "Rise", "Fall", "Stay the same", "Double", ChoiceLetter.B, "Bond prices and interest rates move inversely.", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "Duration measures a bond's sensitivity to:", "Credit risk", "Interest rate changes", "Inflation only", "Liquidity", ChoiceLetter.B, "Duration measures interest rate sensitivity.", topics65[2], Difficulty.medium),
                q(ExamCode.SERIES_65, "A mutual fund that invests in stocks and bonds is typically:", "A money market fund", "A balanced or hybrid fund", "An index fund only", "A sector fund", ChoiceLetter.B, "Balanced/hybrid funds hold both stocks and bonds.", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "An ETF often:", "Trades at NAV only at end of day", "Trades on an exchange like a stock", "Has no ticker", "Cannot be sold short", ChoiceLetter.B, "ETFs trade on exchanges throughout the day.", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "A variable annuity:", "Has a fixed payout", "Offers sub-accounts with market risk", "Is only an insurance product", "Has no tax deferral", ChoiceLetter.B, "Variable annuity has sub-accounts with investment risk.", topics65[2], Difficulty.medium),
                q(ExamCode.SERIES_65, "Municipal bond interest is often:", "Taxable federally", "Exempt from federal income tax", "Taxable only in some states", "Never tax-exempt", ChoiceLetter.B, "Municipal interest is often exempt from federal tax.", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "Common stock represents:", "Debt", "Ownership in the company", "A fixed dividend", "Priority in liquidation", ChoiceLetter.B, "Common stock = equity ownership.", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "Preferred stock typically has:", "No dividend", "Priority over common in dividends", "Same voting as common", "No liquidation preference", ChoiceLetter.B, "Preferred has dividend and often liquidation priority.", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "A REIT typically invests in:", "Stocks only", "Real estate or mortgages", "Bonds only", "Commodities", ChoiceLetter.B, "REIT = real estate investment trust.", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "Diversification can help reduce:", "Systematic risk", "Unsystematic (specific) risk", "All risk", "Inflation risk only", ChoiceLetter.B, "Diversification reduces unsystematic/specific risk.", topics65[2], Difficulty.medium),
                q(ExamCode.SERIES_65, "An index fund seeks to:", "Beat the index", "Match the index's performance", "Only invest in bonds", "Avoid all risk", ChoiceLetter.B, "Index funds seek to replicate index performance.", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "Yield to maturity assumes:", "Bond is sold before maturity", "Bond is held to maturity and coupons reinvested at YTM", "No reinvestment", "Default occurs", ChoiceLetter.B, "YTM assumes hold to maturity and reinvestment at YTM.", topics65[2], Difficulty.medium),
                q(ExamCode.SERIES_65, "Junk bonds refer to:", "Treasury bonds", "High-quality corporate bonds", "Below-investment-grade bonds", "Municipal bonds", ChoiceLetter.C, "Junk = below investment grade (high yield).", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "An open-end mutual fund:", "Has a fixed number of shares", "Issues and redeems at NAV", "Trades only on exchange", "Is a UIT", ChoiceLetter.B, "Open-end funds issue and redeem at NAV.", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "A fixed annuity provides:", "Variable payout based on sub-accounts", "A fixed payout", "Only equity exposure", "No tax deferral", ChoiceLetter.B, "Fixed annuity provides fixed payout.", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "Liquidity risk is:", "The risk that an asset cannot be sold quickly without loss", "Only for bonds", "The same as credit risk", "Only for stocks", ChoiceLetter.A, "Liquidity risk = cannot sell quickly without significant loss.", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "Inflation risk refers to:", "Default risk", "Purchasing power erosion", "Interest rate risk only", "Liquidity risk", ChoiceLetter.B, "Inflation risk = purchasing power loss over time.", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "Asset allocation is:", "The same as stock picking", "Dividing investments among asset classes", "Only for bonds", "Only for retirees", ChoiceLetter.B, "Asset allocation = dividing among asset classes.", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "Risk tolerance is:", "The client's willingness and ability to take risk", "Only age-based", "Only for institutional clients", "The same for everyone", ChoiceLetter.A, "Risk tolerance = willingness and ability to take risk.", topics65[3], Difficulty.easy),
                q(ExamCode.SERIES_65, "A growth-oriented client typically:", "Seeks capital appreciation", "Seeks only income", "Accepts no risk", "Only holds bonds", ChoiceLetter.A, "Growth-oriented clients seek capital appreciation.", topics65[3], Difficulty.easy),
                q(ExamCode.SERIES_65, "Income-oriented strategies emphasize:", "Only capital gains", "Current income (dividends, interest)", "Only growth", "Only speculation", ChoiceLetter.B, "Income strategies emphasize current income.", topics65[3], Difficulty.easy),
                q(ExamCode.SERIES_65, "Time horizon affects:", "Asset allocation and product choice", "Only risk tolerance", "Only liquidity", "Nothing", ChoiceLetter.A, "Time horizon affects allocation and product selection.", topics65[3], Difficulty.easy),
                q(ExamCode.SERIES_65, "A client with a short time horizon and need for liquidity may favor:", "Illiquid alternatives", "Cash and short-term instruments", "Only small-cap stocks", "Only long-term bonds", ChoiceLetter.B, "Short horizon and liquidity needs favor cash/short-term.", topics65[3], Difficulty.easy),
                q(ExamCode.SERIES_65, "Dollar-cost averaging involves:", "Investing a fixed amount at regular intervals", "Only lump-sum investing", "Only when market is high", "Selling only", ChoiceLetter.A, "DCA = investing fixed amount at regular intervals.", topics65[3], Difficulty.easy),
                q(ExamCode.SERIES_65, "Rebalancing is used to:", "Increase risk over time", "Bring portfolio back to target allocation", "Only sell winners", "Only buy more of one asset", ChoiceLetter.B, "Rebalancing restores target allocation.", topics65[3], Difficulty.easy),
                q(ExamCode.SERIES_65, "A conservative investor typically:", "Accepts high volatility for return", "Prefers preservation of capital", "Only holds stocks", "Ignores inflation", ChoiceLetter.B, "Conservative investors prefer capital preservation.", topics65[3], Difficulty.easy),
                q(ExamCode.SERIES_65, "Modern portfolio theory emphasizes:", "Picking only winners", "Diversification and efficient frontier", "Only bonds", "Only one asset class", ChoiceLetter.B, "MPT emphasizes diversification and efficient frontier.", topics65[3], Difficulty.medium),
                q(ExamCode.SERIES_65, "A client's financial situation includes:", "Only income", "Income, assets, liabilities, expenses", "Only assets", "Only age", ChoiceLetter.B, "Financial situation includes income, assets, liabilities, expenses.", topics65[3], Difficulty.easy),
                q(ExamCode.SERIES_65, "Tax considerations may affect:", "Asset location and product choice", "Only income", "Only for high earners", "Nothing", ChoiceLetter.A, "Taxes affect asset location and product choice.", topics65[3], Difficulty.easy),
                q(ExamCode.SERIES_65, "An aggressive growth strategy may include:", "Only money market", "Higher allocation to equities and higher risk", "Only bonds", "Only cash", ChoiceLetter.B, "Aggressive growth often has higher equity allocation.", topics65[3], Difficulty.easy),
                q(ExamCode.SERIES_65, "Retirement planning often considers:", "Only current income", "Time horizon, income needs, inflation, taxes", "Only Social Security", "Only 401(k)", ChoiceLetter.B, "Retirement planning considers horizon, needs, inflation, taxes.", topics65[3], Difficulty.easy),
                q(ExamCode.SERIES_65, "Estate planning considerations may include:", "Only wills", "Taxes, titling, beneficiaries, trusts", "Only life insurance", "Only real estate", ChoiceLetter.B, "Estate planning includes taxes, titling, beneficiaries, trusts.", topics65[3], Difficulty.medium),
                q(ExamCode.SERIES_65, "Correlation in a portfolio refers to:", "Only returns", "How assets move relative to each other", "Only risk", "Only stocks", ChoiceLetter.B, "Correlation = how asset returns move relative to each other.", topics65[3], Difficulty.medium),
                q(ExamCode.SERIES_65, "A moderate risk profile typically:", "Seeks balance of growth and income with moderate risk", "Accepts no risk", "Only holds stocks", "Only holds bonds", ChoiceLetter.A, "Moderate = balance of growth and income, moderate risk.", topics65[3], Difficulty.easy),
                q(ExamCode.SERIES_65, "GDP measures:", "Only inflation", "Total output of goods and services", "Only employment", "Only interest rates", ChoiceLetter.B, "GDP = gross domestic product, total output.", topics65[4], Difficulty.easy),
                q(ExamCode.SERIES_65, "Inflation is typically measured by:", "GDP only", "CPI and/or PCE", "Only unemployment", "Only Fed funds rate", ChoiceLetter.B, "Inflation often measured by CPI and PCE.", topics65[4], Difficulty.easy),
                q(ExamCode.SERIES_65, "When the Fed raises interest rates, bond prices typically:", "Rise", "Fall", "Stay unchanged", "Double", ChoiceLetter.B, "Rate increases typically cause bond prices to fall.", topics65[4], Difficulty.easy),
                q(ExamCode.SERIES_65, "The business cycle includes phases such as:", "Only expansion", "Expansion, peak, contraction, trough", "Only recession", "Only growth", ChoiceLetter.B, "Business cycle: expansion, peak, contraction, trough.", topics65[4], Difficulty.easy),
                q(ExamCode.SERIES_65, "Monetary policy is primarily conducted by:", "The Treasury", "The Federal Reserve", "Congress", "The SEC", ChoiceLetter.B, "Federal Reserve conducts monetary policy.", topics65[4], Difficulty.easy),
                q(ExamCode.SERIES_65, "Fiscal policy involves:", "Interest rate setting", "Government spending and taxation", "Only money supply", "Only bank regulation", ChoiceLetter.B, "Fiscal policy = government spending and taxation.", topics65[4], Difficulty.easy),
                q(ExamCode.SERIES_65, "During a recession, the Fed might:", "Raise rates only", "Lower rates to stimulate economy", "Only sell securities", "Only increase reserve requirements", ChoiceLetter.B, "Fed may lower rates in recession to stimulate.", topics65[4], Difficulty.easy),
                q(ExamCode.SERIES_65, "Real return is approximately:", "Nominal return only", "Nominal return minus inflation", "Inflation only", "Risk-free rate only", ChoiceLetter.B, "Real return ≈ nominal return minus inflation.", topics65[4], Difficulty.medium),
                q(ExamCode.SERIES_65, "Unemployment is a:", "Lagging indicator", "Leading indicator", "Coincident indicator", "None of these", ChoiceLetter.A, "Unemployment is often a lagging indicator.", topics65[4], Difficulty.medium),
                q(ExamCode.SERIES_65, "Yield curve typically slopes upward when:", "Short rates are higher than long rates", "Long rates are higher than short rates", "All rates are equal", "There is deflation", ChoiceLetter.B, "Normal yield curve: long rates > short rates.", topics65[4], Difficulty.medium),
                q(ExamCode.SERIES_65, "An inverted yield curve has sometimes preceded:", "Only inflation", "Recessions", "Only bull markets", "Only bond rallies", ChoiceLetter.B, "Inverted yield curve has preceded recessions.", topics65[4], Difficulty.medium),
                q(ExamCode.SERIES_65, "CPI stands for:", "Corporate Profit Index", "Consumer Price Index", "Central Policy Indicator", "Credit Performance Index", ChoiceLetter.B, "CPI = Consumer Price Index.", topics65[4], Difficulty.easy),
                q(ExamCode.SERIES_65, "Expansionary fiscal policy typically involves:", "Higher taxes and lower spending", "Lower taxes and/or higher spending", "Only rate cuts", "Only Fed action", ChoiceLetter.B, "Expansionary fiscal = lower taxes and/or higher spending.", topics65[4], Difficulty.medium),
                q(ExamCode.SERIES_65, "Deflation is:", "Rising prices", "Falling prices", "Stable prices", "Only for commodities", ChoiceLetter.B, "Deflation = sustained fall in general price level.", topics65[4], Difficulty.easy),
                q(ExamCode.SERIES_65, "The discount rate is:", "The rate the Fed charges banks", "The rate banks charge each other", "Only the prime rate", "Only for mortgages", ChoiceLetter.A, "Discount rate = rate Fed charges banks for loans.", topics65[4], Difficulty.medium),
                q(ExamCode.SERIES_65, "Leading economic indicators may include:", "Stock prices, building permits", "Only GDP", "Only unemployment", "Only CPI", ChoiceLetter.A, "Leading indicators include stock prices, building permits.", topics65[4], Difficulty.medium),
                q(ExamCode.SERIES_65, "Interest rate risk for bonds is the risk that:", "Issuer defaults", "Rates rise and bond prices fall", "Only inflation", "Only liquidity", ChoiceLetter.B, "Interest rate risk = rates rise, bond prices fall.", topics65[4], Difficulty.easy),
                q(ExamCode.SERIES_65, "The Fed's dual mandate includes:", "Only price stability", "Price stability and maximum employment", "Only employment", "Only GDP growth", ChoiceLetter.B, "Fed mandate: price stability and maximum employment.", topics65[4], Difficulty.easy),
                q(ExamCode.SERIES_65, "Supply and demand for loanable funds affect:", "Interest rates", "Only stock prices", "Only inflation", "Only employment", ChoiceLetter.A, "Supply and demand for funds affect interest rates.", topics65[4], Difficulty.easy),
                q(ExamCode.SERIES_65, "Currency risk applies when:", "Investing only domestically", "Investing in foreign assets or foreign currency", "Only in bonds", "Only in stocks", ChoiceLetter.B, "Currency risk applies to foreign investments/currency.", topics65[4], Difficulty.easy),
                q(ExamCode.SERIES_65, "Economic growth is often associated with:", "Only recession", "Corporate earnings and equity performance", "Only bond performance", "Only deflation", ChoiceLetter.B, "Growth often supports earnings and equity performance.", topics65[4], Difficulty.easy),
                q(ExamCode.SERIES_65, "An IA that has custody must generally:", "Have no audit", "Use qualified custodian and/or satisfy custody rule", "Only hold cash", "Only for mutual funds", ChoiceLetter.B, "Custody rule requires qualified custodian and/or surprise exam.", topics65[0], Difficulty.medium),
                q(ExamCode.SERIES_65, "The term 'investment adviser' excludes under the Act:", "Banks and BD agents giving only incidental advice", "Anyone giving advice", "Only state-registered firms", "Only those with AUM over $25M", ChoiceLetter.A, "Act excludes banks and BD agents in certain circumstances.", topics65[0], Difficulty.hard),
                q(ExamCode.SERIES_65, "Form ADV Part 2A is:", "The brochure", "Part 1 only", "Only for SEC-registered", "Not required", ChoiceLetter.A, "Part 2A is the brochure (firm disclosure).", topics65[0], Difficulty.medium),
                q(ExamCode.SERIES_65, "A solicitor is someone who:", "Only provides research", "Refers clients for compensation", "Only holds custody", "Only files ADV", ChoiceLetter.B, "Solicitor refers clients for a fee; disclosure required.", topics65[0], Difficulty.medium),
                q(ExamCode.SERIES_65, "State registration of an IA may be denied or revoked for:", "Only failure to pay fee", "Fraud, conviction, or other statutory grounds", "Only if no clients", "Only if SEC-registered", ChoiceLetter.B, "States may deny/revoke for fraud, conviction, etc.", topics65[0], Difficulty.medium),
                q(ExamCode.SERIES_65, "An IA must update Form ADV:", "Never", "At least annually and when material changes occur", "Only when changing custody", "Only when adding clients", ChoiceLetter.B, "ADV must be updated annually and for material changes.", topics65[0], Difficulty.easy),
                q(ExamCode.SERIES_65, "Disclosure of referral fees to the client must be:", "Only verbal", "In writing before the client is referred or engages", "Only in the brochure", "Only annually", ChoiceLetter.B, "Referral fee disclosure must be in writing in advance.", topics65[1], Difficulty.medium),
                q(ExamCode.SERIES_65, "An IA may not:", "Charge fees", "Make unsuitable recommendations or engage in fraud", "Rebalance", "Use a custodian", ChoiceLetter.B, "IAs must not make unsuitable recommendations or commit fraud.", topics65[1], Difficulty.easy),
                q(ExamCode.SERIES_65, "Personal trading by access persons may require:", "No reporting", "Pre-approval and reporting of holdings and trades", "Only annual report", "Only for principals", ChoiceLetter.B, "Code of ethics typically requires reporting and possibly pre-approval.", topics65[1], Difficulty.medium),
                q(ExamCode.SERIES_65, "Scalping in the IA context refers to:", "Rebalancing", "Buying for own account then recommending to clients", "Selling only", "Dollar-cost averaging", ChoiceLetter.B, "Scalping = buying then recommending to clients for profit.", topics65[1], Difficulty.medium),
                q(ExamCode.SERIES_65, "A wrap account sponsor typically:", "Has no fiduciary duty", "Provides program; IA may have fiduciary duty to client", "Only holds custody", "Only executes trades", ChoiceLetter.B, "Wrap sponsor provides program; IA has duty to client.", topics65[1], Difficulty.medium),
                q(ExamCode.SERIES_65, "Beta measures a security's:", "Total risk", "Volatility relative to the market", "Only dividend yield", "Only liquidity", ChoiceLetter.B, "Beta = sensitivity to market (systematic risk).", topics65[2], Difficulty.medium),
                q(ExamCode.SERIES_65, "A closed-end fund:", "Redeems at NAV daily", "Trades on exchange; may trade at premium or discount", "Has no ticker", "Is the same as an open-end fund", ChoiceLetter.B, "Closed-end funds trade on exchange at market price.", topics65[2], Difficulty.medium),
                q(ExamCode.SERIES_65, "Unit investment trusts:", "Have active management", "Have a fixed portfolio", "Trade at NAV only", "Are the same as mutual funds", ChoiceLetter.B, "UIT has fixed, unmanaged portfolio.", topics65[2], Difficulty.medium),
                q(ExamCode.SERIES_65, "Credit risk is the risk that:", "Interest rates rise", "The issuer defaults", "Inflation rises", "The bond is liquid", ChoiceLetter.B, "Credit risk = issuer default.", topics65[2], Difficulty.easy),
                q(ExamCode.SERIES_65, "A 403(b) plan is typically for:", "Corporate employees", "Employees of public schools and certain nonprofits", "Only government", "Only self-employed", ChoiceLetter.B, "403(b) is for public schools and certain nonprofits.", topics65[2], Difficulty.medium),
                q(ExamCode.SERIES_65, "Tax-loss harvesting involves:", "Selling winners only", "Selling losers to offset gains", "Only in IRAs", "Only for bonds", ChoiceLetter.B, "Tax-loss harvesting = selling losers to offset gains.", topics65[3], Difficulty.medium),
                q(ExamCode.SERIES_65, "Asset location refers to:", "Where the client lives", "Placing assets in taxable vs tax-advantaged accounts", "Only geographic", "Only bonds in IRA", ChoiceLetter.B, "Asset location = which account holds which assets for tax efficiency.", topics65[3], Difficulty.medium),
                q(ExamCode.SERIES_65, "A 60/40 stock/bond allocation is often considered:", "Very aggressive", "Moderate or balanced", "Very conservative", "Only for young investors", ChoiceLetter.B, "60/40 is often considered moderate/balanced.", topics65[3], Difficulty.easy),
                q(ExamCode.SERIES_65, "Systematic risk cannot be diversified away because:", "It is firm-specific", "It affects the whole market", "Only bonds have it", "Only stocks have it", ChoiceLetter.B, "Systematic risk = market-wide, not diversifiable.", topics65[3], Difficulty.medium),
                q(ExamCode.SERIES_65, "An emergency fund is typically:", "Invested in long-term bonds", "Kept in liquid, low-risk assets", "Only in stocks", "Only in real estate", ChoiceLetter.B, "Emergency fund = liquid, low-risk.", topics65[3], Difficulty.easy),
                q(ExamCode.SERIES_65, "The 4% rule is often cited for:", "Initial withdrawal rate in retirement", "Asset allocation", "Only bond yield", "Only inflation", ChoiceLetter.A, "4% rule = sustainable withdrawal rate in retirement.", topics65[3], Difficulty.medium),
                q(ExamCode.SERIES_65, "Fed funds rate is the rate:", "Banks charge customers", "Banks charge each other for overnight loans", "On Treasury bills", "On mortgages", ChoiceLetter.B, "Fed funds = interbank overnight rate.", topics65[4], Difficulty.medium),
                q(ExamCode.SERIES_65, "Quantitative easing (QE) typically involves:", "Raising rates", "Central bank buying securities to inject liquidity", "Only fiscal policy", "Only tax cuts", ChoiceLetter.B, "QE = central bank buying securities to increase money supply.", topics65[4], Difficulty.medium),
                q(ExamCode.SERIES_65, "A recession is often defined as:", "One quarter of negative GDP", "Two consecutive quarters of negative real GDP", "Only rising unemployment", "Only falling stock prices", ChoiceLetter.B, "Recession often = two consecutive quarters of negative real GDP.", topics65[4], Difficulty.easy),
                q(ExamCode.SERIES_65, "Stagflation refers to:", "Growth with low inflation", "Stagnation and high inflation", "Only deflation", "Only employment growth", ChoiceLetter.B, "Stagflation = stagnation + inflation.", topics65[4], Difficulty.medium),
                q(ExamCode.SERIES_65, "The prime rate is:", "The Fed funds rate", "Rate banks charge their best customers", "Only for mortgages", "Only for bonds", ChoiceLetter.B, "Prime rate = rate banks charge best customers.", topics65[4], Difficulty.medium)
            )
            if (questionRepository.findByExamCodeAndActiveTrue(ExamCode.SERIES_65).isEmpty()) {
                series65Qs.forEach { questionRepository.save(it) }
            }

            log.info("Coach: seed complete (exams and questions by exam code).")
        } catch (e: Exception) {
            log.warn("Could not seed coach data (Mongo may be down): {}", e.message)
        }
    }
}
