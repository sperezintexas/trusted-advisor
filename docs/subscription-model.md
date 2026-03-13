# Subscription model

This document defines how Trusted Advisor represents plans today and how plan selection is applied during registration.

## Current plans

| Tier | Price (USD/month) | Included features |
|------|--------------------|-------------------|
| `BASIC` | `0.00` | Exam Coach access, practice exams, basic chat, 30 coach questions, 10 chat questions |
| `PREMIUM` | `9.99` | Everything in Basic, AI Tutor sessions, priority support, unlimited coach questions, unlimited chat questions |

## Source of truth

- Backend policy lives in `app/src/main/kotlin/com/atxbogart/trustedadvisor/service/SubscriptionPolicy.kt`.
- User role persisted in MongoDB `users.role` (`ADMIN`, `BASIC`, `PREMIUM`).
- Registration endpoint applies selected tier through the policy: `POST /api/auth/register`.

## Registration behavior

When a user submits registration with a `tier`:

1. Tier is normalized case-insensitively.
2. Unknown/blank values default to `BASIC`.
3. If the existing user is `ADMIN`, role remains `ADMIN` (admin role is sticky).
4. For non-admin users:
   - `BASIC` -> `UserRole.BASIC`
   - `PREMIUM` -> `UserRole.PREMIUM`

## Read-only plan API

The backend exposes plan metadata for clients:

- `GET /api/auth/subscription/plans`

Response shape:

```json
{
  "plans": [
    {
      "tier": "BASIC",
      "displayName": "Basic",
      "monthlyPriceUsd": "0.00",
      "features": ["Exam Coach access", "Practice exams", "Basic chat"]
    },
    {
      "tier": "PREMIUM",
      "displayName": "Premium",
      "monthlyPriceUsd": "9.99",
      "features": ["Everything in Basic", "AI Tutor sessions", "Priority support"]
    }
  ]
}
```

## Notes

- This model controls application role and feature gating only.
- Payment collection is not implemented in this branch; `PREMIUM` is a role assignment at registration time.
- Usage limits are enforced in backend via `UsageLimitService`:
  - BASIC: max 30 coach questions and max 10 chat questions
  - PREMIUM/ADMIN: unlimited
