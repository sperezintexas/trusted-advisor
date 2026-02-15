# Trusted Advisor

A conversational AI assistant powered by **xAI Grok**, with expert personas and built-in **securities license exam prep** (SIE, Series 7, Series 57). Use it for advice, research, and timed practice exams with instant feedback and explanations.

---

## What you can do

- **Chat with expert personas** — Choose or create personas (e.g. finance, legal, tax). Turn on live web search and market data (Yahoo Finance) per persona. View token usage when you want.
- **Practice for license exams** — Take timed, multiple-choice practice or full-length exams. Check each answer (right/wrong highlighted), read explanations, and review your attempt history and scores.
- **SIE tutor** — From the Coach page, start an SIE “Tutor session” to open Chat with a study-focused prompt and the Options Exam Coach persona.

---

## Get started (local with .env)

1. Copy `.env.example` to `.env` and set **AUTH_SECRET** (login password), **XAI_API_KEY**, and **MONGODB_URI** (or **MONGODB_URI_B64**) for your remote Mongo.
2. **Backend:** from repo root run `./gradlew bootRun`
3. **Frontend:** `cd frontend && npm install --legacy-peer-deps && npm run dev`
4. Open **http://localhost:3000** and sign in with your AUTH_SECRET value.

Chat, Personas, Config, and Coach (including full practice exams) all work. The frontend proxies `/api` to the backend.

**Docker:** optional — `docker compose up --build` runs both; see [DEVELOPMENT.md](DEVELOPMENT.md) for details and troubleshooting.

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
