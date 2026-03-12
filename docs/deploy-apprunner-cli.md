# App Runner one-time setup (CLI only)

Use this when you have AWS CLI configured (e.g. `aws sts get-caller-identity` works) and want to set up App Runner **without the AWS console**. Your IAM user can be admin (or have ECR + App Runner + IAM role create/attach permissions).

## Order of operations

Run from **repo root**. Default region is `us-east-1` unless you set `AWS_REGION` or pass it.

### 1. ECR repos

Creates `trusted-advisor-backend` and `trusted-advisor-frontend` in ECR.

```bash
./scripts/aws-ecr-setup.sh
# or: ./scripts/aws-ecr-setup.sh us-east-1
```

### 2. App Runner ECR access role

Creates IAM role `AppRunnerECRAccess` so App Runner can pull images from ECR. Script prints the role ARN.

```bash
./scripts/iam-apprunner-ecr-role.sh
# export the ARN it prints:
export APP_RUNNER_ECR_ACCESS_ROLE=arn:aws:iam::YOUR_ACCOUNT:role/AppRunnerECRAccess
```

### 3. First images in ECR

App Runner needs at least one image per repo before you can create the service. Pick one:

**Option A — Push to `main` (easiest)**  
In GitHub: set **Secrets** `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` for this repo. Push to `main`; CI will build and push both images to ECR. Wait for the "Build and push to ECR" job to finish, then continue with step 4.

**Option B — Build and push locally**  
From repo root (Docker required, images must be `linux/amd64` for App Runner):

```bash
./scripts/aws-ecr-push-local.sh us-east-1
```

That script logs in to ECR, builds backend and frontend, and pushes `trusted-advisor-backend:latest` and `trusted-advisor-frontend:latest`. Then continue with step 4.

### 4. Create backend App Runner service

Uses `APP_RUNNER_ECR_ACCESS_ROLE` from step 2. Service is created with **no env vars**; you add them in step 6.

```bash
export APP_RUNNER_ECR_ACCESS_ROLE=arn:aws:iam::YOUR_ACCOUNT:role/AppRunnerECRAccess
./scripts/create-apprunner-service-backend.sh us-east-1
```

Wait until the service is **RUNNING**:

```bash
aws apprunner list-services --region us-east-1 --query 'ServiceSummaryList[?ServiceName==`trusted-advisor-backend`].[ServiceArn,Status,ServiceUrl]' --output table
# or poll: aws apprunner describe-service --service-arn <ARN> --region us-east-1 --query 'Service.Status' --output text
```

Save the **backend Service ARN** and **Service URL** (e.g. `https://xxxxx.us-east-1.awsapprunner.com`).

### 5. Create frontend App Runner service

Pass the **backend URL** from step 4 so the frontend can call the API. Replace `https://xxxxx.us-east-1.awsapprunner.com` with your backend URL.

```bash
./scripts/create-apprunner-service-frontend.sh us-east-1 'https://xxxxx.us-east-1.awsapprunner.com'
```

Wait until status is **RUNNING**. Save the **frontend Service ARN** and **Service URL**.

### 6. Push env vars to backend and frontend

Create a **production env file** (e.g. `.env.prod`) with the same keys as your app (e.g. `MONGODB_URI`, `MONGODB_DATABASE`, `AUTH_SECRET`, `XAI_API_KEY`, `FRONTEND_URL`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, etc.). Set `FRONTEND_URL` to your **frontend** App Runner URL. Do not commit this file.

Then push those vars into each service (triggers a new deployment each time):

```bash
# Backend
./scripts/update-apprunner-env.sh .env.prod <BACKEND_SERVICE_ARN> us-east-1

# Frontend (include BACKEND_URL and NEXT_PUBLIC_BACKEND_URL = backend App Runner URL)
./scripts/update-apprunner-env.sh .env.prod <FRONTEND_SERVICE_ARN> us-east-1
```

Wait for both deployments to finish (status **RUNNING**).

### 7. GitHub Actions

In the repo: **Settings → Secrets and variables → Actions**. Use **repository** Variables (the "Variables" tab), not Environment-specific variables.

- **ARNs:** Paste with no trailing space or newline. If deploy fails with "resource arn is invalid", re-enter the value in one line and save again.

- **Secrets:** `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
- **Variables:**  
  - `ENABLE_AWS_DEPLOY` = `true`  
  - `APP_RUNNER_SERVICE_ARN_BACKEND` = backend Service ARN  
  - `APP_RUNNER_SERVICE_ARN_FRONTEND` = frontend Service ARN  
  - (optional) `AWS_REGION` = `us-east-1`

After that, every successful CI run on `main` will trigger an App Runner deployment for both services.

## Script reference

| Script | Purpose |
|--------|--------|
| `aws-ecr-setup.sh [region]` | Create ECR repos for backend and frontend |
| `aws-ecr-push-local.sh [region]` | Build both images (linux/amd64) and push to ECR from your machine |
| `iam-apprunner-ecr-role.sh [role-name]` | Create IAM role for App Runner ECR pull (default: `AppRunnerECRAccess`) |
| `create-apprunner-service-backend.sh [region]` | Create backend App Runner service (requires `APP_RUNNER_ECR_ACCESS_ROLE`) |
| `create-apprunner-service-frontend.sh [region] [backend-url]` | Create frontend App Runner service |
| `update-apprunner-env.sh <env-file> <service-arn> [region]` | Set service env vars from file (requires `jq`) |

Generated files `apprunner-backend-input.json` and `apprunner-frontend-input.json` are in `.gitignore` (they can contain secrets).

## Troubleshooting

- **"Invalid Access Role" / "Failed to copy the image from ECR"**  
  The role from `iam-apprunner-ecr-role.sh` must have trust for `build.apprunner.amazonaws.com` and the managed policy `AWSAppRunnerServicePolicyForECRAccess`. Re-run the script or fix the role in IAM.

- **Health check fails (backend)**  
  Backend exposes `/health`. If startup is slow, switch to TCP:  
  `aws apprunner update-service --service-arn <ARN> --region us-east-1 --health-check-configuration '{"Protocol":"TCP","Interval":10,"Timeout":5,"HealthyThreshold":1,"UnhealthyThreshold":5}'`

- **Frontend can’t reach backend**  
  Ensure `BACKEND_URL` and `NEXT_PUBLIC_BACKEND_URL` on the frontend service are set to the **backend** App Runner URL (https), and that Google/GitHub OAuth redirect URIs include your frontend URL.
