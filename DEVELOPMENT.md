# Development Guide

## Prerequisites

- **Backend:** JDK 21+, Gradle (wrapper: `./gradlew`), MongoDB (URI in `.env`).
- **Frontend:** Node 18+ (optional for local dev; required for Docker).
- **Docker (optional):** Docker and Docker Compose for one-command run.

**UI routes:** `/chat` (personas, Grok), `/personas`, `/config` (debug, test xAI), `/coach` (exam list), `/coach/[examCode]` (practice/full exam), `/coach/history`.

## Configuration

- **App config:** `app/src/main/resources/application.yml` — port, Spring name, MongoDB default, xAI key placeholder, logging.
- **Secrets:** One `.env` at the **repo root** is used by both backend and frontend. Copy from `.env.example`; do not commit `.env`.
  - **Backend:** Spring Boot loads it via spring-dotenv (working dir is repo root when running `./gradlew bootRun`).
  - **Frontend:** Next.js loads it via `dotenv` in `next.config.mjs` (path: `../.env`). Set `MONGODB_URI` (or `MONGODB_URI_B64`), `XAI_API_KEY`; optional `BACKEND_URL` for API proxy.

## Running the App

### Docker (easiest)

```bash
docker compose up --build
```

- App: <http://localhost:3000> (frontend; `/api` → backend).
- Backend: <http://localhost:8080>.
- Uses local MongoDB in compose unless you override `MONGODB_URI` in `.env`.
- Frontend starts only after backend is healthy; all services use `restart: unless-stopped`.
- Faster rebuilds: `DOCKER_BUILDKIT=1 docker compose build` uses cache mounts for Gradle and npm.

### Local

```bash
# Backend (from project root; .env loaded from here)
./gradlew bootRun
```

App at <http://localhost:8080>. Quick check: `curl http://localhost:8080/` and `curl http://localhost:8080/api/personas`.

### Frontend (optional, with backend already running)

**Start the backend first** (see above), then:

```bash
cd frontend && npm install --legacy-peer-deps && npm run dev
```

Open <http://localhost:3000>; rewrites send `/api/*` to the backend (or set `BACKEND_URL` for Docker backend).

If you see **"Failed to proxy … socket hang up"** or **ECONNRESET** when using Chat or other `/api/*` features, the backend is likely not running or crashed. Start it with `./gradlew bootRun` from the project root.

## Build & Test

```bash
./gradlew build    # includes tests
./gradlew test     # tests only
```

**Frontend:** `cd frontend && npm run lint && npx tsc --noEmit && npm run test`.

## CI (GitHub Actions)

On push/PR to `main`/`master`, [.github/workflows/ci.yml](.github/workflows/ci.yml) runs:

- **Backend:** JDK 21, Gradle cache, `./gradlew build`.
- **Frontend:** Node 20, `npm ci`, lint, typecheck, test.
- **Security:** Dependency review (PRs; fail on high), npm audit (high), Gitleaks secret scan (config: [.gitleaks.toml](.gitleaks.toml)).

## Adding Features

- **New endpoint:** Add a `@RestController` under `com.atxbogart.trustedadvisor.controller`.
- **Dependencies:** Edit `gradle/libs.versions.toml` and `app/build.gradle.kts`, then `./gradlew build --refresh-dependencies`.

## IDE

- **IntelliJ:** Open as Gradle project; JDK 21; re-import after changing `build.gradle.kts` or `libs.versions.toml`.
- **VS Code:** Java Extension Pack, Gradle for Java.

## Database

- MongoDB; URI from `application.yml` / `.env`. Collections: `personas`, `chatHistory` (see [docs/database-schema.md](docs/database-schema.md)).

## AI (Grok/xAI)

- `XAI_API_KEY` in `.env` → `xai.api.key`. Chat uses personas from MongoDB and `GrokService` for Grok calls.
