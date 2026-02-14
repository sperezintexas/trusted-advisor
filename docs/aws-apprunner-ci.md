# AWS App Runner — GitHub CI

The workflow [../.github/workflows/deploy-aws-apprunner.yml](../.github/workflows/deploy-aws-apprunner.yml) builds the **backend** and **frontend** Docker images (linux/amd64), pushes them to Amazon ECR, and optionally triggers App Runner deployments.

## When it runs

- **Push** to `main` or `master`
- **Manual:** Actions → Deploy to AWS App Runner → Run workflow

## What it does

1. **Build and push** (always, if AWS creds are present):
   - Backend image from `Dockerfile.backend` → ECR repo `trusted-advisor-backend` (or `ECR_REPOSITORY_BACKEND`)
   - Frontend image from `frontend/Dockerfile` → ECR repo `trusted-advisor-frontend` (or `ECR_REPOSITORY_FRONTEND`)
   - Tags: `latest` and git SHA
   - ECR repos are created if they don’t exist

2. **Deploy** (only if enabled):
   - If `ENABLE_AWS_DEPLOY` is `true` and `APP_RUNNER_SERVICE_ARN_BACKEND` is set → starts backend App Runner deployment and waits for it.
   - If `ENABLE_AWS_DEPLOY` is `true` and `APP_RUNNER_SERVICE_ARN_FRONTEND` is set → starts frontend App Runner deployment and waits for it.

## GitHub setup

### Secrets (Settings → Secrets and variables → Actions → Secrets)

| Secret | Description |
|--------|-------------|
| `AWS_ACCESS_KEY_ID` | IAM user access key (ECR + App Runner permissions). |
| `AWS_SECRET_ACCESS_KEY` | IAM user secret key. |

### Variables (Settings → Secrets and variables → Actions → Variables)

| Variable | Default | Description |
|----------|---------|-------------|
| `ENABLE_AWS_DEPLOY` | — | Set to `true` to run deploy steps. Omit or `false` = build & push only. |
| `AWS_REGION` | `us-east-1` | Region for ECR and App Runner. |
| `ECR_REPOSITORY_BACKEND` | `trusted-advisor-backend` | ECR repository name for backend image. |
| `ECR_REPOSITORY_FRONTEND` | `trusted-advisor-frontend` | ECR repository name for frontend image. |
| `APP_RUNNER_SERVICE_ARN_BACKEND` | — | App Runner service ARN for backend (port 8080). If set and deploy enabled, triggers deployment after push. |
| `APP_RUNNER_SERVICE_ARN_FRONTEND` | — | App Runner service ARN for frontend (port 3000). If set and deploy enabled, triggers deployment after push. |

## One-time AWS setup

1. **IAM:** User with ECR (create repo, push image) and App Runner (`StartDeployment`, `DescribeService`, `ListOperations`) permissions. See [aws-app-runner-requirements.md](aws-app-runner-requirements.md) for a policy example (replace `myinvestments` with `trusted-advisor-backend` / `trusted-advisor-frontend` in the ECR resource ARNs, or use `*` for the repo name pattern).

2. **App Runner services:** Create one or two services in the AWS console (or CLI):
   - **Backend:** Source = ECR `trusted-advisor-backend`, tag `latest`; port **8080**; health check path `/health`; set env (e.g. `MONGODB_URI`, `XAI_API_KEY`). Deployment trigger = Manual.
   - **Frontend:** Source = ECR `trusted-advisor-frontend`, tag `latest`; port **3000**; health check path `/` or TCP; set `BACKEND_URL` to the backend App Runner URL. Deployment trigger = Manual.

3. **ECR access role:** Each App Runner service needs an IAM role that can pull from ECR (trust `build.apprunner.amazonaws.com`; attach `AWSAppRunnerServicePolicyForECRAccess` or equivalent). See [aws-app-runner-requirements.md](aws-app-runner-requirements.md) “Fix ECR access role”.

4. Copy the **Service ARN** for each service into the GitHub variables above.

## Flow summary

- Push to `main` → workflow runs → builds both images → pushes to ECR with `latest` and SHA.
- If deploy is enabled and backend ARN is set → `aws apprunner start-deployment` for backend → wait until operation succeeds.
- If deploy is enabled and frontend ARN is set → same for frontend.
- App Runner pulls the new `latest` image and rolls out; if health check fails, it rolls back.

## Notes

- **Backend** needs MongoDB (e.g. Atlas). Set `MONGODB_URI` (and optionally `MONGODB_DATABASE`) in the App Runner service environment.
- **Frontend** must have `BACKEND_URL` set to the backend’s public URL (e.g. the backend App Runner URL) so `/api/*` rewrites work.
- Images are built for **linux/amd64** only (App Runner requirement).
