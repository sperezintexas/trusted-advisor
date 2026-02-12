# Smart Chat Advisor

AI chat advisor for generic advice, powered by xAI Grok. Combines configurable tools (web search, market data, scheduled tasks) with a customizable prompts.

## Overview

- **Page**: `/chat` — Smart Grok Chat
- **API**: `POST /api/chat` — chat with conversation history
- **History**: `GET /api/chat/history` — load saved messages (per user)
- **Config**: `GET/PUT /api/chat/config` — tools and context
- **Storage**: Config in MongoDB `appUtil` collection (`key: grokChatConfig`); chat history in `chatHistory` collection (per user)

## Rate limits (all chat APIs)

Apply rate limits to **every** chat-related API to protect backend, LLM, and external services. Use the same key (e.g. user ID or IP + user when unauthenticated) for consistency.

| Endpoint | Suggested limit | Rationale |
|----------|-----------------|-----------|
| `POST /api/chat` | 20 req/min per client | LLM + tools cost; avoid spam and runaway loops. |
| `GET /api/chat/history` | 60 req/min per client | Read-heavy; allow refresh but prevent scraping. |
| `GET /api/chat/config` | 60 req/min per client | Config rarely changes; avoid hammering DB. |
| `PUT /api/chat/config` | 10 req/min per client | Mutations; prevent config thrashing. |

- **Implementation**: Middleware or wrapper that checks a sliding-window or token-bucket counter keyed by `userId` (authenticated) or `ip` / `fingerprint` (anonymous). Return `429 Too Many Requests` with `Retry-After` when exceeded.
- **Response**: On 429, return JSON `{ error: "rate_limit_exceeded", retryAfter: number }` and set header `Retry-After: <seconds>`.
- **Client**: On 429, disable send button and show “Too many requests; try again in Xs” with optional countdown; back off on history/config fetches (e.g. exponential backoff or simple retry-after).

## Chat UI

- **Placeholder**: The input shows “Smart Chat — Ask about anything choose your persona and mood. Powered by xAI [model]” (model from config, e.g. grok-4).
- **Example prompts**: A collapsible “Example prompts” panel below the input (collapsed by default to save space) groups suggestions by tool:
  - **Web search**: Whats the Weather in Austin, TX next week?
  - **Quotes & market**: TSLA price, AAPL quote, Market outlook, VIX level, SPY and QQQ today
  - **Tasks & scan**: manage (CRUD) Scheduled tasks, show tasks, new task
- Tapping an example fills the input; user can edit or send. Layout is mobile-friendly (wrapping chips, scrollable panel).

## Chat interface: optimizations & best practices

- **Debounce / single-flight**: Only one in-flight `POST /api/chat` per session; disable send while waiting. Optional: debounce rapid “example prompt” clicks so one message is sent.
- **Optimistic UI**: Append user message to the list immediately; show loading state for the assistant bubble. On error or 429, revert or show error in-place and re-enable send.
- **History window**: Send only last N messages (e.g. 10) in the request to keep payload and context size bounded; trim on server to last 50 per user as already specified.
- **Config caching**: Cache `GET /api/chat/config` on the client (e.g. SWR with `revalidateOnFocus: false` and 5–10 min revalidate). Invalidate on successful `PUT`.
- **Streaming (optional)**: If supported, stream assistant tokens and render incrementally to reduce perceived latency; keep rate limit on the single `POST` that opens the stream.
- **Accessibility**: Ensure input has `aria-label`, example chips are keyboard-focusable and activatable with Enter/Space, and rate-limit/error messages are announced (e.g. `aria-live`).
- **Errors**: Surface clear messages for 429 (rate limit), 5xx (server/LLM), and timeouts; offer “Retry” and do not leave the send button permanently disabled except on 429 with countdown.

## Tools

Two kinds of tools:

### 1. LLM Tool (Web Search)

Defined in `src/lib/xai-grok.ts` as `WEB_SEARCH_TOOL` (OpenAI-compatible function tool):

```ts
{
  name: "web_search",
  description: "Search the web for current information: weather, news, earnings dates, analyst views, real-time data. Use for company/news (e.g. 'TSLA earnings', 'Tesla FSD news') when not in provided context.",
  parameters: { query: string, num_results?: number }
}
```

- **When**: Grok decides to call it during the chat completion loop (up to 3 rounds).
- **Executor**: `executeWebSearch()` → `searchWeb()` in `web-search.ts` (SerpAPI).
- **Config**: `tools.webSearch` (default: true). Requires `WEB_SEARCH_API_KEY` or `SERPAPI_API_KEY`.
- **Examples for Grok**: `TSLA earnings date 2026`, `Tesla stock news today`, `BA defense sector outlook`, `current weather Austin TX`.

### 2. LLM Tools (Schedule Tasks)

When schedule tools are enabled (default: true), Grok can call:

| Tool | Description | When Grok uses it |
|------|--------------|-------------------|
| **list_tasks** | List scheduled tasks (Unified Options Scanner, Watchlist, Grok prompts, etc.), cron schedules, status, next run times | User asks about tasks, schedules, automation, scanners, next run |
| **trigger_portfolio_scan** | Run full portfolio options evaluation now (Unified Options Scanner + watchlist + deliver alerts) | User explicitly asks to run scan, evaluate positions now, check my options |

- **Config**: Tools for tasks are enabled via chat config (Setup → Chat config or `/api/chat/config`).
- **Keywords that help trigger**: tasks, schedules, automation, scanners, next run, run scan, evaluate positions now, check my options.

## Default System Prompt

Built in `POST /api/chat`:

```
You are Zoltan, an all knowing expert and personal advisor for myTrustedAdvisor. You advise on based on the person selected.
```

Appended dynamically:

- When `tools.webSearch` is true: for current prices use the pre-injected [Context from tools] data first; use web_search only for earnings, news, sentiment, or facts not in context.
- **Data freshness:** Grok is instructed to use ONLY the REAL-TIME (LIVE/CURRENT) data in the context for prices; training data is treated as outdated.

**Override**: `context.systemPromptOverride` (max 4000 chars) replaces the base prompt when non-empty.

## Config Schema

```ts
{
  tools: {
    webSearch: boolean;      // default true — LLM tool (SerpAPI)
    // Schedule tools (default on): list_tasks
  };
  context: {
    riskProfile?: "low" | "medium" | "high" | "aggressive";  // default "medium"
    strategyGoals?: string;   // max 2000 chars
    systemPromptOverride?: string;  // max 4000 chars
    persona?: string;        // default "finance-expert" — finance-expert | medical-expert | legal-expert | tax-expert | trusted-advisor
  };
}
```

## Chat History

- **Where to find it**: Visit the [Chat](/chat) page — your last conversation restores automatically when you open it.
- **Persistence**: Each exchange (user message + assistant response) is saved to MongoDB `chatHistory` collection, keyed by user ID.
- **Resume**: On load, the chat page fetches `GET /api/chat/history` and restores the last conversation.
- **Multi-turn context**: When sending a message, the client passes the last 10 messages as `history`. The API prepends this to the user content so Grok has conversation context.
- **Limit**: History is trimmed to the last 50 messages per user.

## Flow

1. User opens chat → `GET /api/chat/history` loads saved messages
2. User sends message → `POST /api/chat` with `{ message, history }`
3. **Rate limit check** for the route (see [Rate limits](#rate-limits-all-chat-apis))
4. Load `getGrokChatConfig()`
5. Intent detection → run enabled pre-fetch tools (portfolio, market news, stock prices, covered call recommendations when applicable)
6. Build system prompt (default or override + risk + goals + tools hint)
7. Build user content: `{history}\n[Context from tools]\n{context}\n\n[User question]\n{message}` (history = last 10 messages when provided)
8. Call `callGrokWithTools(systemPrompt, userContent, { tools: webSearch ? [WEB_SEARCH_TOOL] : [] })`
9. Grok may call `web_search`; executor runs SerpAPI and appends results to messages
10. Save user message + response to chat history (when user is authenticated)
11. Return `{ response, toolResults? }`

## Fallback

If Grok returns empty, hits tool loop limit, or throws: `buildFallbackResponse()` returns formatted tool context plus disclaimer, or a generic “try asking about…” message.

## Tool Keywords Reference

Use these keywords in your message to trigger pre-fetch tools or help Grok choose LLM tools:

| Tool | Keywords |
|------|----------|
| **Market News** | market, news, outlook, trending, sentiment, conditions, indices |
| **Stock Prices** | price, quote, stock, option, trading, how much, current, value — or mention a ticker (e.g. TSLA) |
| **Portfolio & Watchlist** | portfolio, holdings, positions, account, balance, watchlist, watching, tracking |
| **Risk Analysis** | risk, var, beta, sharpe, diversification, volatility, stress, analyze portfolio |
| **Covered Call Recommendations** | covered call, my calls, scanner, recommendations, btc, roll, assign, expiration, should I btc/roll/close |
| **Schedule tasks (list)** | tasks, schedules, automation, scanners, next run, when does X run |
| **Schedule tasks (run scan)** | run scan, evaluate positions now, check my options, run options scanner |

## Example Prompts (by tool)

These match the collapsible example panel in the UI (see **Chat UI** above).


## Environment

- `XAI_API_KEY` — required for Grok
- `WEB_SEARCH_API_KEY` or `SERPAPI_API_KEY` — optional, for web search tool

**Backend config file:** `app/src/main/resources/application.yml` (server port, Spring name, MongoDB URI, `xai.api.key`). Values are overridden by env vars or by a `.env` file in the project root (loaded at startup via spring-dotenv).

## Testing

- **`TEST_CHAT_PERSONA`** (e.g. `zoltan`) — persona used when running chat-related tests.
- **`TEST_CHAT_CLEAN=true`** — when set, chat history must be **removed during testing** so tests run against a clean slate:
  - **`GET /api/chat/history`**: Return empty messages (or clear history for the test user before the test run).
  - **`POST /api/chat`**: Do not persist user/assistant messages to `chatHistory` when this flag is true, or clear the test user’s history before/after each test.
  - Implement by checking `process.env.TEST_CHAT_CLEAN === 'true'` (or equivalent in your stack) in the history and chat handlers: skip loading/saving history, or explicitly delete history for the test user at the start of the test suite or each test.
