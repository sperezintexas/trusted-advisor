# Trusted Advisor

A conversational AI assistant powered by **xAI Grok**, with expert personas and built-in **securities license exam prep** (SIE, Series 7, Series 57). Use it for advice, research, and timed practice exams with instant feedback and explanations.

---

## What you can do

- **Chat with expert personas** — Choose or create personas (e.g. finance, legal, tax). Turn on live web search and market data (Yahoo Finance) per persona. View token usage when you want.
- **Practice for license exams** — Take timed, multiple-choice practice or full-length exams. Check each answer (right/wrong highlighted), read explanations, and review your attempt history and scores.
- **SIE tutor** — From the Coach page, start an SIE “Tutor session” to open Chat with a study-focused prompt and the Options Exam Coach persona.

---

## Get started

**Easiest: run with Docker**

1. Copy `.env.example` to `.env` and add your **xAI API key** (and MongoDB URI if you’re not using the default local database).
2. Run: `docker compose up --build`
3. Open **http://localhost:3000** in your browser.

You’ll see the main app (Chat, Personas, Config, Coach). The frontend talks to the backend automatically.

**Other options (local backend + frontend, IDE setup, tests):** see **[DEVELOPMENT.md](DEVELOPMENT.md)** for full setup, run commands, and troubleshooting.

---

## Documentation

| For | Document |
|-----|----------|
| **Setup, run, build, test** | [DEVELOPMENT.md](DEVELOPMENT.md) |
| **Chat behavior and config** | [docs/chat-advisor.md](docs/chat-advisor.md) |
| **License exam prep (Coach)** | [docs/coach-license-exams.md](docs/coach-license-exams.md) |
| **Database collections** | [docs/database-schema.md](docs/database-schema.md) |
| **API reference & coding guide** | [AGENTS.md](AGENTS.md) |
| **Deploy to AWS App Runner** | [docs/aws-apprunner-ci.md](docs/aws-apprunner-ci.md) |

Technical details (endpoints, APIs, stack, AWS and Cursor rules) are in the docs above.
