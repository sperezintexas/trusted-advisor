# Development Guide

## Prerequisites

- **Backend:** JDK 21+, Gradle (wrapper: `./gradlew`), MongoDB (URI in `.env`).
- **Frontend:** Node 18+ (optional for local dev; required for Docker).
- **Docker (optional):** Docker and Docker Compose for one-command run.

**UI routes:** `/chat` (personas, Grok), `/personas`, `/config` (debug, test xAI), `/coach` (exam list), `/coach/[examCode]` (exam page; full practice UI in dev only—see below), `/coach/history`.

## Configuration

- **App config:** `app/src/main/resources/application.yml` — port, Spring name, MongoDB default, xAI key placeholder, logging.
- **Secrets:** One `.env` at the **repo root** is used by both backend and frontend. Copy from `.env.example`; do not commit `.env`.
  - **Backend:** Spring Boot loads it via spring-dotenv (working dir is repo root when running `./gradlew bootRun`).
  - **Frontend:** Next.js loads it via `dotenv` in `next.config.mjs` (path: `../.env`). Set `MONGODB_URI` (or `MONGODB_URI_B64`), `XAI_API_KEY`; optional `BACKEND_URL` for API proxy.
  - **Auth:** Set **AUTH_SECRET** (shared secret; user enters it on the login page). **Debug:** set `AUTH_DEBUG=true` (backend) and/or `NEXT_PUBLIC_AUTH_DEBUG=true` (frontend), restart; logs `[auth]` to console (local only). (Legacy: **X / Twitter OAuth2** — Set `X_CLIENT_ID`, `X_CLIENT_SECRET` (create an app at [developer.x.com](https://developer.x.com/)). You can use the **same X app** as fintech-app: in the portal add a **second** callback URL for this app: `http://localhost:8080/login/oauth2/code/x` (fintech uses `http://localhost:3000/api/auth/callback/twitter`; trusted-advisor uses the backend on 8080). Callback in the portal must match exactly. **Debug:** set `AUTH_DEBUG=true` (backend) and/or `NEXT_PUBLIC_AUTH_DEBUG=true` (frontend), restart; logs `[auth]` to console (local only). If you get 400, try 127.0.0.1 (see “X OAuth 2.0 login — 400” below).
  - **X posting (optional):** For `npm run test:x-post` (verify credentials + post "Hello world"), set `X_CONSUMER_KEY`, `X_CONSUMER_SECRET` (or `X_CONSUMER_SECRET_KEY`), `X_ACCESS_TOKEN`, `X_ACCESS_TOKEN_SECRET` from developer.x.com → Keys and tokens.

## Running the App (local with .env)

**Backend** (from repo root; `.env` is loaded from here):

```bash
./gradlew bootRun
```

Backend at <http://localhost:8080>. Quick check: `curl http://localhost:8080/` and `curl http://localhost:8080/api/personas`.

**Frontend** (with backend already running):

```bash
cd frontend && npm install --legacy-peer-deps && npm run dev
```

Open <http://localhost:3000>; rewrites send `/api/*` to the backend. Sign in with the value of `AUTH_SECRET` from `.env`.

### Docker (optional)

```bash
docker compose up --build
```

- App: <http://localhost:3000>. Backend: <http://localhost:8080>.
- Reads `.env` (AUTH_SECRET, XAI_API_KEY, MONGODB_URI). No local MongoDB in compose (use remote Mongo).
- **Coach exam:** Docker production build uses a simplified coach exam page; for full practice exam UI, run the frontend locally with `npm run dev`.

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

## API key auth

**Replaced X OAuth2.** Set **`AUTH_SECRET`** in `.env` (e.g. `openssl rand -hex 32`). The backend accepts it in the **`X-API-Key`** header or **`Authorization: Bearer <key>`** on `/api/*`. The login page prompts for this key; the frontend stores it in sessionStorage and sends it with every request. Authenticated requests use a synthetic user id `api-user`.

---

## (Removed) X OAuth 2.0 — kept for reference only

**Same X app as fintech:** Add a **second** callback in the X portal: `http://localhost:8080/login/oauth2/code/x` (fintech keeps `http://localhost:3000/api/auth/callback/twitter`). Leave `OAUTH2_REDIRECT_URI` unset to use localhost like fintech. Debug: `AUTH_DEBUG` + `NEXT_PUBLIC_AUTH_DEBUG` → `[auth]` in console.

If you see **`[authorization_request_not_found]`** after X redirects back to the app: you started the flow from a direct Twitter URL (e.g. from `node scripts/test-env.mjs`) instead of from the app. Always start by opening the app and clicking **Sign in with X** so the backend creates the session; the script URL is only for checking `redirect_uri` / portal config.

If you see **"Something went wrong"** on the X authorize page (and `redirect_uri` in the URL is already `http://localhost:8080/login/oauth2/code/x`): the callback is almost always not registered or not exact in the X portal. In [developer.x.com](https://developer.x.com/) → your app → **User authentication** → **Callback URI / Redirect URL**, add exactly `http://localhost:8080/login/oauth2/code/x` (no trailing slash). Ensure **OAuth 2.0** is enabled, **Type of App** is e.g. Web App, and in Development mode add your X account to the **Allowlist**. Then try again.

If you **don’t see the “Authorize app” page** and get **400** on `twitter.com/i/api/2/oauth2/authorize` or `api.twitter.com/1.1/onboarding/referrer.json`, Twitter is rejecting the request. Often **using 127.0.0.1 instead of localhost** fixes it:

1. In **.env** set:
   - `OAUTH2_REDIRECT_URI=http://127.0.0.1:8080/login/oauth2/code/x`
   - `FRONTEND_URL=http://127.0.0.1:3000`
   - `NEXT_PUBLIC_BACKEND_URL=http://127.0.0.1:8080`
2. In **X Developer Portal** → your app → User authentication → Callback URI, add **exactly** `http://127.0.0.1:8080/login/oauth2/code/x` (you can keep the localhost one).
3. Restart backend and frontend, then open the app at **http://127.0.0.1:3000** (not localhost) and click “Sign in with X” again.

If you still see **400** with `redirect_uri=http://127.0.0.1:8080/...` (correct redirect_uri but no Authorize app page):

1. **Get the exact error from X**  
   DevTools → **Network** → click the red **400** request to `twitter.com/i/api/2/oauth2/authorize` → **Response** (or **Preview**) tab. Copy the response body (e.g. `{"errors":[{"message":"..."}]}` or "Redirect is requested"). That tells you what X is rejecting.
2. **Callback must be exact in the portal**  
   In [developer.x.com](https://developer.x.com/) → your app → User authentication → **Callback URI / Redirect URL**, the entry must be exactly `http://127.0.0.1:8080/login/oauth2/code/x` (no trailing slash, no space). Copy-paste from here; even a small typo causes 400.
3. **Development mode → Allowlist**  
   If the app is in **Development**, your X account must be in **User authentication → Allowlist**. Otherwise X returns 400 or "Something went wrong".

4. **"Redirect is requested" (400 with `error_description: "Redirect is requested."`)**  
   X’s consent page makes an internal API call that returns this; it’s a known quirk. Try: (A) In the X portal, set **App details → Website URL** to e.g. `http://127.0.0.1:3000`. (B) Create a **new app** (new Project or under existing), set User authentication (OAuth 2.0, Web App, callback `http://127.0.0.1:8080/login/oauth2/code/x`, Allowlist if Development), then use the new app’s Client ID and Secret in `.env`. (C) Try in an **incognito/private** window or another browser.

If you still see **400** on `twitter.com/i/api/2/oauth2/authorize` or `api.twitter.com/1.1/onboarding/referrer.json` (general):

1. **Get the exact error**  
   DevTools → Network → click the red **400** request → **Response** (or **Preview**) tab. Note the body (e.g. `"Redirect is requested"`, invalid `redirect_uri`, or a JSON error). That tells you what X is rejecting.

2. **Portal checklist** ([developer.x.com](https://developer.x.com/) → your app):
   - **Projects & Apps:** Use an app that is **inside a Project** (not a standalone legacy app).
   - **User authentication:** Click **Set up** or **Edit**. Enable **OAuth 2.0**; set **Type of App** to one that supports “Log in with X” (e.g. Web App).
   - **Callback URL:** List must include **both** `http://localhost:3000/api/auth/callback/twitter` (fintech) and `http://localhost:8080/login/oauth2/code/x` (this app). Or use 127.0.0.1 if localhost gives 400. No trailing slashes.
   - **App permissions:** At least **Read**.
   - **Development mode:** If the app is in Development, add your X account under **Allowlist** (User authentication).
   - **Keys:** Use the **OAuth 2.0 Client ID** and **Client Secret** from **User authentication** → Keys and tokens (not the older Consumer Keys).

3. **This repo:** Backend uses PKCE (`require-proof-key: true`) and `OAUTH2_REDIRECT_URI` for 127.0.0.1. Open the app at `http://127.0.0.1:3000` and use the same callback in the portal.

4. **Try a new app:** If the current app was created before “Log in with X” or is in a bad state, create a **new app** under a Project, set User authentication (OAuth 2.0 + callback URL), then put the new Client ID and Secret in `.env`.
