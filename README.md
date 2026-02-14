# Trusted Advisor

AI trusted advisor with expert personas and exam-prep coaching. Chat powered by xAI Grok; practice exams (e.g. SIE, Series 7) with Check Answer, explanations, and history.

## Features

- **Multi-persona chat** — Custom personas; select in chat. Optional web search and Yahoo Finance per persona.
- **Chat API** — `POST /api/chat`, `GET /api/chat/history`; responses include optional usage (tokens). History in MongoDB.
- **Chat config** — `GET/PUT /api/chat/config` (debug flag, tools, context); `GET /api/chat/config/test` to verify xAI connection.
- **Personas API** — `GET/POST/PUT/DELETE /api/personas`; persona tools (web search, Yahoo Finance) configurable.
- **Coach (exam prep)** — Practice and full exams (SIE, Series 7, Series 57): timed, multiple choice, Check Answer (green/amber feedback), Show Explanation, attempt history and scoring.
- **MongoDB** — Personas, chat history, coach questions/attempts/progress (see [database-schema](docs/database-schema.md)).

## Quick Start

### Option A: Docker (recommended)

```bash
cp .env.example .env   # set XAI_API_KEY (and MONGODB_URI if not using local Mongo)
docker compose up --build
```

- **App:** [http://localhost:3000](http://localhost:3000) — frontend; `/api/*` proxied to backend.
- **Backend:** [http://localhost:8080](http://localhost:8080).
- **MongoDB:** `localhost:27017` (compose starts Mongo; set `MONGODB_URI` in `.env` for Atlas).
- Frontend starts after backend is healthy; use `DOCKER_BUILDKIT=1` for faster rebuilds.

### Option B: Local

1. Clone repo; copy env: `cp .env.example .env` and set `MONGODB_URI`, `XAI_API_KEY`.
2. **Backend:** `./gradlew bootRun` (from repo root; loads `.env`) → [http://localhost:8080](http://localhost:8080).
3. **Frontend:** `cd frontend && npm install --legacy-peer-deps && npm run dev` → [http://localhost:3000](http://localhost:3000) for `/chat`, `/personas`, `/config`, `/coach`.

## Configuration

- **Secrets:** One `.env` at **repo root** is used by both backend and frontend. Copy `.env.example`; do not commit `.env`.
- **Backend:** `application.yml` — port, MongoDB default, xAI key; spring-dotenv loads `.env` when running `./gradlew bootRun` (working dir = root).
- **Frontend:** Next.js loads repo-root `.env` via `next.config.mjs`; optional `BACKEND_URL` for API proxy.

## Endpoints

| Path | Description |
|------|-------------|
| `/`, `/health` | Welcome, health check |
| `GET/POST /api/personas`, `GET/PUT/DELETE /api/personas/:id` | Personas CRUD |
| `POST /api/chat` | Send message (`message`, optional `personaId`, `userId`); returns `response`, optional `usage` |
| `GET /api/chat/history?userId=` | Chat history |
| `GET/PUT /api/chat/config` | Get/update config (debug, tools, context) |
| `GET /api/chat/config/test` | Test xAI/Grok connection |
| `GET /api/coach/exams` | List exams |
| `GET /api/coach/exams/{code}/practice-session?count=` | Practice session questions |
| `GET /api/coach/exams/{code}/check?questionId=&selectedLetter=` | Check answer (correct, correctLetter, explanation) |
| `POST /api/coach/exams/{code}/score` | Score exam (body: answers, optional save, userId) |
| `GET /api/coach/history`, `GET /api/coach/exams/{code}/history` | Attempt history |

## Tech Stack

- **Backend:** Kotlin 2.0.20, Spring Boot 3.3.4, Gradle, Java 21; MongoDB; xAI Grok API.
- **Frontend:** Next.js 14 App Router, TypeScript, Tailwind.

## Docs

- [DEVELOPMENT.md](DEVELOPMENT.md) — Run, build, test, Docker, IDE.
- [AGENTS.md](AGENTS.md) — Guide for coding agents.
- [docs/chat-advisor.md](docs/chat-advisor.md) — Chat flow, config, tools.
- [docs/database-schema.md](docs/database-schema.md) — MongoDB collections.
- [docs/coach-license-exams.md](docs/coach-license-exams.md) — Coach: license exam prep (SIE, Series 7, 57), flow, API, data model.
- [.cursor/rules/coach-exam-prep.mdc](.cursor/rules/coach-exam-prep.mdc) — Coach/exam prep behavior.
- [docs/aws-apprunner-ci.md](docs/aws-apprunner-ci.md) — GitHub CI: build images, push to ECR, deploy to App Runner.
- [docs/aws-app-runner-*.md](docs/) — AWS App Runner and scheduler notes.
