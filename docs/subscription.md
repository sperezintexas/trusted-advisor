# Subscription basics

Trusted Advisor currently supports two plan tiers:

- `BASIC` - free tier
- `PREMIUM` - paid tier (`$9.99/month`)

## What each tier includes

### BASIC

- Exam Coach access
- Practice exams
- Basic chat
- Up to 30 coach questions total
- Up to 10 chat questions total

### PREMIUM

- Everything in BASIC
- AI Tutor sessions
- Priority support
- Full or practice exams as often as needed
- Unlimited coach questions
- Unlimited chat questions

## How subscription is applied today

- Plan selection happens during registration (`/register` UI -> `POST /api/auth/register`).
- The selected tier is mapped to the user role in the backend:
  - `BASIC` -> `UserRole.BASIC`
  - `PREMIUM` -> `UserRole.PREMIUM`
- Unknown plan values safely default to `BASIC`.
- `ADMIN` users are never downgraded by tier selection.

## Current scope

- Subscription currently controls in-app role/tier behavior.
- Payment processing is not implemented in this repository yet.

## Related docs

- Detailed policy and API: `docs/subscription-model.md`
- Test matrix and validation steps: `docs/subscription-test-cases.md`
