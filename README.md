# Trusted Advisor (fintechadvisor.ai)

Trusted Advisor is an AI-powered coaching platform for financial exam prep and professional decision support. It combines expert personas, live intelligence, and structured practice workflows to help people learn faster, make better decisions, and perform with confidence.

Official domain: [fintechadvisor.ai](https://fintechadvisor.ai)

---

## Why teams and learners use Trusted Advisor

- **Faster exam readiness** with guided practice for SIE, Series 7, Series 57, and Series 65.
- **Higher confidence under pressure** through timed exam simulation and instant explanations.
- **Smarter learning loops** with progress history, score trends, and targeted improvement.
- **Scalable enablement** for firms that need consistent, high-quality training outcomes.
- **Always-on support** so users can practice, ask questions, and refine understanding anytime.

---

## Core features

- **AI Chat with expert personas**  
  Interact with finance-focused advisors tailored to different goals and communication styles.

- **Persona-specific knowledge context**  
  Attach structured reference content to personas so responses can use role-specific context.

- **Exam Coach experience**  
  Run realistic practice sessions and full exams with immediate correctness checks and explanations.

- **Tutor mode for focused study**  
  Launch guided coaching flows that adapt to weak areas and reinforce key concepts.

- **Progress tracking**  
  Review performance over time, identify gaps, and prioritize next study actions.

---

## Business impact

- **Reduce failure-related cost** from repeated exam attempts and delayed productivity.
- **Accelerate onboarding** for new advisors and licensed roles.
- **Standardize training quality** across teams, cohorts, and locations.
- **Improve retention** with clear milestones and measurable progress.

---

## Product snapshot

![Exam Coach - Practice for FINRA licensing exams](docs/coachexams.jpg)
![Exam Coach - Grok hints during timed practice](docs/hintscoach2.png)

---

## Subscription

Trusted Advisor supports two plans:

| Feature | BASIC (Free) | PREMIUM ($9.99/mo) |
| --- | ---: | ---: |
| Coach questions | Up to **30 total** per user | **Unlimited** |
| Chat questions | Up to **10 total** per user | **Unlimited** |
| Practice sessions | Included (within question cap) | **Unlimited practice sessions** |
| Full exams | Included (within question cap) | **Unlimited full exams** |
| AI Tutor sessions | — | Included |
| Priority support | — | Included |

> **How limits work:** BASIC limits are account-level totals. PREMIUM removes those limits.

### Which plan should I pick?

- **BASIC**: Best for trialing the platform and light exam prep.
- **PREMIUM**: Best for active prep with unlimited tests, unlimited practice sessions, and unlimited chat.

Start free on [fintechadvisor.ai](https://fintechadvisor.ai), then upgrade to PREMIUM when you need unlimited practice and chat.

---

## Monetization

Trusted Advisor monetizes through a freemium subscription model on `fintechadvisor.ai`:

- `BASIC` keeps onboarding friction low with free access and capped usage.
- `PREMIUM` unlocks unlimited tests, unlimited practice sessions, unlimited coach questions, and unlimited chat.
- Access-request and admin approval workflows support controlled user acquisition and conversion.

---

## Documentation

| For | Document |
| ----- | ---------- |
| **Chat behavior and config** | [docs/chat-advisor.md](docs/chat-advisor.md) |
| **License exam prep (Coach)** | [docs/coach-license-exams.md](docs/coach-license-exams.md) |
| **Subscription basics** | [docs/subscription.md](docs/subscription.md) |
| **Subscription plans and behavior** | [docs/subscription-model.md](docs/subscription-model.md) |
| **Subscription test matrix** | [docs/subscription-test-cases.md](docs/subscription-test-cases.md) |
| **Database collections** | [docs/database-schema.md](docs/database-schema.md) |
| **API reference & coding guide** | [AGENTS.md](AGENTS.md) |
| **Deploy to AWS App Runner** | [docs/aws-apprunner-ci.md](docs/aws-apprunner-ci.md) |

Technical details (endpoints, APIs, stack, AWS and Cursor rules) are in the docs above.
