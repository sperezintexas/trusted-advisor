# AGENTS.md

Repository guide for autonomous coding agents working on **Trusted Advisor**.

## 1) Project Snapshot

- **Backend:** Kotlin + Spring Boot (`/app`)
- **Frontend:** Next.js 14 App Router + TypeScript (`/frontend`)
- **Database:** MongoDB (`personas`, `chatHistory`, coach: `coachExams`, `coachQuestions`, `coachUserProgress`, `coachExamAttempts` — see `docs/database-schema.md`)
- **Primary API:** `/api/chat`, `/api/chat/history`, `/api/chat/config`, `/api/chat/config/test`, `/api/personas`, `/api/coach/*` (exams, practice-session, check, score, history)

## 2) Directory Map

- `app/src/main/kotlin/com/atxbogart/trustedadvisor`
  - `controller/` — ChatController, PersonaController, CoachController, GlobalExceptionHandler
  - `service/` — ChatService, GrokService, PersonaService, ChatConfigService, CoachService
  - `repository/` — Mongo repositories
  - `model/` — DTOs and domain types
- `app/src/main/resources/application.yml` — backend config
- `frontend/app/` — routes: `/chat`, `/personas`, `/config`, `/coach`, `/coach/[examCode]`, `/coach/history`
- `docs/` — product and architecture notes

## 3) Setup and Run

1. Copy env file: `cp .env.example .env`
2. Set required secrets in `.env`:
   - `MONGODB_URI` or `MONGODB_URI_B64` (base64-encoded URI)
   - `XAI_API_KEY`
   - `AUTH_SECRET` (shared API key; user enters it on the login page)

Run modes:

- **Docker (recommended):** `docker compose up --build`
- **Backend local (repo root):** `./gradlew bootRun`
- **Frontend local (from `frontend/`):** `npm install --legacy-peer-deps && npm run dev`

**CLI env test (repo root):** `node scripts/test-env.mjs` — loads `.env`, reports AUTH_SECRET and tests xAI if `XAI_API_KEY` set.

## 4) Validation Commands (Run Before Commit)

Choose commands by touched area:

- **Backend changes:**
  - `./gradlew test`
  - `./gradlew build` (if dependency/config changes)
- **Frontend changes** (from `frontend/`):
  - `npm run lint`
  - `npx tsc --noEmit`
  - `npm test`

Note: frontend `npm test` is currently a placeholder command that returns success when no tests exist.

## 5) Coding Rules for Agents

### Scope and safety

- Make minimal, focused changes for the task only.
- Do not refactor unrelated modules.
- Do not modify unrelated docs/files.
- Preserve backward-compatible API behavior unless task explicitly requires a breaking change.

### TypeScript / Next.js

- Keep code type-safe (no `any` unless unavoidable and justified).
- Use **type aliases** instead of interfaces for shape declarations.
- Prefer Server Components; use `'use client'` only when needed.
- Keep App Router conventions (`app/.../page.tsx`, layouts, route handlers).

### Kotlin / Spring

- Keep controller → service → repository separation.
- Prefer immutable data handling and explicit DTO models.
- Keep endpoints and JSON contracts stable unless task says otherwise.

### Data and security

- Never commit secrets or `.env`.
- Do not hardcode API keys or tokens.
- Keep rate-limiting and validation behavior intact when changing chat/persona APIs.

## 6) API and Behavior Notes

- **Chat:** `POST /api/chat` returns `{ response, usage? }`. Persona (optional `personaId`) sets system prompt and tool hints (web search, Yahoo Finance). History in MongoDB.
- **Config:** `GET/PUT /api/chat/config` — in-memory config (debug, tools, context). `GET /api/chat/config/test` — test xAI connection (no recording).
- **Coach:** Practice/full exams per exam code (SIE, SERIES_7, SERIES_57). `GET .../check?questionId=&selectedLetter=` returns correct/correctLetter/explanation (no progress recording). `POST .../score` scores and optionally saves attempt.
- Never commit `.env`; both backend and frontend use repo-root `.env`.

## 7) Commit and Push Checklist

1. Run relevant validation commands.
2. Stage only intended files.
3. Commit with a clear, conventional message (example: `docs: add repository AGENTS guide`).
4. Push current branch to origin.

If behavior changes, include a short note in commit message scope or PR summary about affected endpoints.

## Cursor Cloud specific instructions

### Environment prerequisites

- **Java 21**, **Node 22+**, and **MongoDB 7.0** are pre-installed on the VM.
- The update script runs `npm install --legacy-peer-deps` (frontend) and `./gradlew build --no-daemon -x test` (backend) on each startup.

### `.env` setup (one-time per VM snapshot)

Copy `.env.example` to `.env` and populate:

- `AUTH_SECRET` — generate with `openssl rand -hex 32`.
- `MONGODB_URI=mongodb://localhost:27017/trusted-advisor` — local MongoDB.
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` — set to any non-empty dummy value (e.g. `dummy`). Spring Security OAuth2 auto-config fails at startup if these are empty, even though OAuth login is optional. This is the most common startup gotcha.
- `XAI_API_KEY` — required only for chat (Grok LLM) functionality. Without it the backend starts fine but `/api/chat` will return errors.

### Starting services

1. **MongoDB:** `sudo mongod --dbpath /data/db --fork --logpath /var/log/mongodb/mongod.log`
2. **Backend (repo root):** `./gradlew bootRun --no-daemon` (port 8080)
3. **Frontend (`frontend/`):** `npm run dev` (port 3000, proxies `/api/*` to backend)

### Validation (see §4 above)

Standard lint/typecheck/test commands are documented in section 4. No backend unit tests currently exist (`./gradlew test` is a no-op). Frontend `npm test` is a placeholder.

### Gotchas

- The `.env` file lives at the **repo root** and is shared by both services. The backend's `bootRun` task sets `workingDir` to the repo root so spring-dotenv picks it up; Next.js loads `../.env` via `next.config.mjs`.
- Frontend must be installed with `--legacy-peer-deps` due to peer dependency conflicts.
- There is no `package-lock.json` committed for the frontend; CI uses `npm ci` with a cache path that expects one, but local dev uses `npm install`.
- If the `MONGODB_URI` secret is injected as a **base64-encoded** string, place it in `MONGODB_URI_B64` (not `MONGODB_URI`). The backend's `App.kt` decodes it at startup. Using the wrong field silently connects to localhost instead of the intended remote cluster.
- Local MongoDB is only needed when no remote `MONGODB_URI` / `MONGODB_URI_B64` is configured. Start it with `sudo mongod --dbpath /data/db --fork --logpath /var/log/mongodb/mongod.log`.
