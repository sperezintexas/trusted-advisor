# Trusted Advisor

AI trusted advisor with expert personas (Lawyer, Doctor, Tax Advisor, Finance Expert, Trusted Multi-Expert). Powered by Grok chat.

## Features

- **Multi-persona chat** — Predefined and custom personas; select in chat.
- **Chat API** — `POST /api/chat`, `GET /api/chat/history`; history stored in MongoDB.
- **Personas API** — `GET/POST/PUT/DELETE /api/personas` to manage custom personas.
- **MongoDB** — Chat history, personas, chat config (see [database-schema](docs/database-schema.md)).

## Quick Start

### Option A: Docker (recommended)

```bash
cp .env.example .env   # set XAI_API_KEY (and MONGODB_URI if not using local Mongo)
docker compose up --build
```

- App: [http://localhost:3000](http://localhost:3000) — frontend with `/api` proxied to backend.
- Backend: [http://localhost:8080](http://localhost:8080).
- MongoDB: `localhost:27017` (compose starts a local Mongo; override `MONGODB_URI` in `.env` to use Atlas).

### Option B: Local

1. Clone repo; copy env: `cp .env.example .env` and set `MONGODB_URI`, `XAI_API_KEY`.
2. **Backend:** `./gradlew bootRun` → [http://localhost:8080](http://localhost:8080).
3. **Frontend (optional):** `cd frontend && npm install --legacy-peer-deps && npm run dev` → [http://localhost:3000](http://localhost:3000) for `/chat` and `/personas`.

## Configuration

- **Backend:** `app/src/main/resources/application.yml` — server port, Spring name, MongoDB default, xAI key placeholder, logging.
- **Secrets:** `.env` in project root (loaded by spring-dotenv). Set `MONGODB_URI`, `XAI_API_KEY`. Do not commit `.env`; use `.env.example` as template.

## Endpoints

| Path | Description |
|------|-------------|
| `/`, `/health` | Welcome, health check |
| `GET/POST /api/personas`, `GET/PUT/DELETE /api/personas/:id` | List, create, get, update, delete personas |
| `POST /api/chat` | Send message (body: `message`, optional `personaId`, `userId`) |
| `GET /api/chat/history?userId=` | Chat history for user |

## Tech Stack

- **Backend:** Kotlin 2.0.20, Spring Boot 3.3.4, Gradle, Java 21; MongoDB, xAI Grok API.
- **Frontend:** Next.js 14, Tailwind (optional).

## Docs

- [DEVELOPMENT.md](DEVELOPMENT.md) — Run, build, test, Docker, IDE.
- [docs/chat-advisor.md](docs/chat-advisor.md) — Chat flow, tools, rate limits, config schema, testing.
- [docs/database-schema.md](docs/database-schema.md) — Collections and schema.
- [docs/aws-app-runner-*.md](docs/) — AWS App Runner and scheduler notes.
