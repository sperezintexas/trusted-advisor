# Subscription test cases

Use these cases to verify the subscription model behavior end-to-end.

## Automated coverage

Automated unit tests:

- `app/src/test/kotlin/com/atxbogart/trustedadvisor/service/SubscriptionPolicyTest.kt`

Covered scenarios:

1. Unknown/blank tier defaults to `BASIC`
2. Tier parsing accepts case-insensitive `PREMIUM`
3. Admin role remains `ADMIN` regardless of selected tier
4. Non-admin role mapping follows selected tier
5. Published plan catalog contains both `BASIC` and `PREMIUM`
6. BASIC chat usage is limited to 10 user questions
7. BASIC coach usage is limited to 30 total questions
8. PREMIUM chat usage is unlimited

Run:

```bash
./gradlew -p app test --tests "*SubscriptionPolicyTest"
```

## Manual API test cases

### Task: Check a user's access request with curl

Use this when you need to verify whether a specific user has already submitted an access request.

```bash
export API_URL="http://localhost:8080/api"
export AUTH_SECRET="<AUTH_SECRET>"
export USER_EMAIL="<user@example.com>"
```

Requester-side check:

```bash
curl -s "$API_URL/auth/access-request/status" \
  -H "X-API-Key: $AUTH_SECRET" \
  -H "X-User-Id: $USER_EMAIL"
```

Expected response example:

```json
{
  "hasRequest": true,
  "status": "PENDING",
  "createdAt": "2026-03-12T19:42:13.104"
}
```

Admin-side cross-check (optional):

```bash
export ADMIN_EMAIL="<admin@example.com>"
curl -s "$API_URL/admin/access-requests?status=PENDING" \
  -H "X-API-Key: $AUTH_SECRET" \
  -H "X-User-Id: $ADMIN_EMAIL"
```

### Case 1: Plan catalog endpoint returns both plans

```bash
curl -s http://localhost:8080/api/auth/subscription/plans
```

Expected:
- HTTP `200`
- `plans` includes `BASIC` and `PREMIUM`
- `monthlyPriceUsd` is `0.00` and `9.99`

### Case 2: Register as BASIC

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <AUTH_SECRET>" \
  -H "X-User-Id: <approved-user-email>" \
  -d '{"username":"basic-user","displayName":"Basic User","tier":"BASIC"}'
```

Expected:
- HTTP `200`
- `user.role` is `BASIC`
- Mongo `users.role` persisted as `BASIC`

### Case 3: Register as PREMIUM

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <AUTH_SECRET>" \
  -H "X-User-Id: <approved-user-email>" \
  -d '{"username":"premium-user","displayName":"Premium User","tier":"PREMIUM"}'
```

Expected:
- HTTP `200`
- `user.role` is `PREMIUM`
- Mongo `users.role` persisted as `PREMIUM`

### Case 4: Unknown tier safely falls back to BASIC

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <AUTH_SECRET>" \
  -H "X-User-Id: <approved-user-email>" \
  -d '{"username":"fallback-user","displayName":"Fallback User","tier":"ENTERPRISE"}'
```

Expected:
- HTTP `200`
- `user.role` is `BASIC`

### Case 5: Admin cannot be demoted by registration tier

Precondition:
- user exists with role `ADMIN`

Action:
- call register with `tier: "BASIC"` (or `"PREMIUM"`)

Expected:
- HTTP `200`
- `user.role` stays `ADMIN`

### Case 6: BASIC chat limit reached after 10 messages

Precondition:
- user role is `BASIC`

Action:
- send 10 successful `POST /api/chat` requests
- send 1 additional `POST /api/chat`

Expected:
- 11th request returns HTTP `403`
- response message indicates BASIC chat limit was reached

### Case 7: BASIC coach limit reached after 30 questions

Precondition:
- user role is `BASIC`

Action:
- complete enough coach activity to reach 30 total asked questions
- request another session: `GET /api/coach/exams/{examCode}/practice-session?count=20`

Expected:
- once 30 is reached, further session requests return HTTP `403`

### Case 8: PREMIUM is unlimited

Precondition:
- user role is `PREMIUM`

Expected:
- no hard limit response from `/api/chat` or `/api/coach/exams/{examCode}/practice-session` based on usage count
