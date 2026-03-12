# App Runner: clean deploy from zero (trusted-advisor)

Follow in order. Replace `YOUR_ACCOUNT_ID` with your AWS account ID (`aws sts get-caller-identity --query Account --output text`) and `us-east-1` with your region if different.

**Canonical guide:** For the full CLI-only flow with the scripts in this repo, see [deploy-apprunner-cli.md](deploy-apprunner-cli.md). This doc is a condensed checklist.

---

## Step 1 — ECR repos

From repo root:

```bash
./scripts/aws-ecr-setup.sh us-east-1
```

Creates `trusted-advisor-backend` and `trusted-advisor-frontend` in ECR.

---

## Step 2 — ECR access role (App Runner)

From repo root (requires IAM permissions to create roles):

```bash
./scripts/iam-apprunner-ecr-role.sh
# Export the ARN it prints:
export APP_RUNNER_ECR_ACCESS_ROLE=arn:aws:iam::YOUR_ACCOUNT_ID:role/AppRunnerECRAccess
```

---

## Step 3 — First images in ECR

Either push to `main` and let CI build and push, or from repo root:

```bash
./scripts/aws-ecr-push-local.sh us-east-1
```

---

## Step 4 — Create backend App Runner service

```bash
./scripts/create-apprunner-service-backend.sh us-east-1
```

Wait until status is **RUNNING**. Get backend Service ARN and Service URL (e.g. `https://xxxxx.us-east-1.awsapprunner.com`).

Push env vars:

```bash
./scripts/update-apprunner-env.sh .env.prod <BACKEND_SERVICE_ARN> us-east-1
```

---

## Step 5 — Create frontend App Runner service

```bash
./scripts/create-apprunner-service-frontend.sh us-east-1 'https://<BACKEND_APP_RUNNER_URL>'
```

Wait until status is **RUNNING**. Push env vars:

```bash
./scripts/update-apprunner-env.sh .env.prod <FRONTEND_SERVICE_ARN> us-east-1
```

---

## Step 6 — GitHub Actions

- **Secrets:** `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
- **Variables:** `ENABLE_AWS_DEPLOY=true`, `APP_RUNNER_SERVICE_ARN_BACKEND=<backend ARN>`, `APP_RUNNER_SERVICE_ARN_FRONTEND=<frontend ARN>`

Push to `main` to build, push to ECR, and trigger App Runner deployments.

---

## If deployment fails (health check)

- **Backend:** If `/health` times out, switch to TCP:  
  `aws apprunner update-service --service-arn <BACKEND_ARN> --region us-east-1 --health-check-configuration '{"Protocol":"TCP","Interval":10,"Timeout":5,"HealthyThreshold":1,"UnhealthyThreshold":5}'`  
  Then `aws apprunner start-deployment --service-arn <BACKEND_ARN> --region us-east-1`.
- **Frontend:** Uses TCP health by default. Check **Application logs** in the App Runner console for startup errors (e.g. missing BACKEND_URL).

---

## Quick checklist

| # | Step |
|---|------|
| 1 | ECR repos: `aws-ecr-setup.sh` |
| 2 | Role **AppRunnerECRAccess**: `iam-apprunner-ecr-role.sh` |
| 3 | Images in ECR: push to main or `aws-ecr-push-local.sh` |
| 4 | Backend service: `create-apprunner-service-backend.sh` → RUNNING → `update-apprunner-env.sh` |
| 5 | Frontend service: `create-apprunner-service-frontend.sh` (with backend URL) → RUNNING → `update-apprunner-env.sh` |
| 6 | GitHub: secrets + APP_RUNNER_SERVICE_ARN_BACKEND, APP_RUNNER_SERVICE_ARN_FRONTEND, ENABLE_AWS_DEPLOY |
