# App Runner deploy requirements (you have AWS creds — what’s next)

You have an IAM user with access keys. Follow this order.

---

## 1. IAM permissions for the deploy user

Attach a policy that allows **ECR** (build/push) and **App Runner** (deploy + describe).

**Option A — inline policy (recommended)**

IAM → Users → **trusted-advisor-deploy** (or your user) → Add permissions → Create inline policy → JSON:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ECR",
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken"
      ],
      "Resource": "*"
    },
    {
      "Sid": "ECRRepo",
      "Effect": "Allow",
      "Action": [
        "ecr:CreateRepository",
        "ecr:DescribeRepositories",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload"
      ],
      "Resource": [
        "arn:aws:ecr:*:YOUR_ACCOUNT_ID:repository/trusted-advisor-backend",
        "arn:aws:ecr:*:YOUR_ACCOUNT_ID:repository/trusted-advisor-frontend"
      ]
    },
    {
      "Sid": "AppRunner",
      "Effect": "Allow",
      "Action": [
        "apprunner:ListServices",
        "apprunner:DescribeService",
        "apprunner:StartDeployment",
        "apprunner:ListOperations"
      ],
      "Resource": "*"
    }
  ]
}
```

Replace `YOUR_ACCOUNT_ID` with your AWS account ID (run `aws sts get-caller-identity --query Account --output text`).
`GetAuthorizationToken` must be `Resource: "*"`. The App Runner block uses `Resource: "*"` because `ListServices` does not support resource-level permissions. If you get **AccessDeniedException** on other App Runner actions, ensure the statement includes all required actions (e.g. `UpdateService`, `PauseService`, `ResumeService` if you use them).

**Option B — managed policies**

Attach:

- **AmazonEC2ContainerRegistryPowerUser** (or **AmazonEC2ContainerRegistryFullAccess**)
- **AWSAppRunnerFullAccess** (or a custom policy with the three App Runner actions above)

---

## 2. One-time: get images into ECR

App Runner needs at least one image per ECR repo before you can create the services.

**Option A — push from your machine**

```bash
# From repo root
./scripts/aws-ecr-setup.sh us-east-1
./scripts/aws-ecr-push-local.sh us-east-1
```

See [deploy-apprunner-cli.md](deploy-apprunner-cli.md) for details.

**Option B — let CI push**

1. In GitHub: set **Secrets** `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`. Push to `main`; CI builds and pushes `trusted-advisor-backend` and `trusted-advisor-frontend` to ECR.
2. Do **not** set `ENABLE_AWS_DEPLOY` or the App Runner ARN variables until the services are created and RUNNING.

After this, ECR repos `trusted-advisor-backend` and `trusted-advisor-frontend` must have the `latest` (or a specific) tag.

---

## 3. Create the App Runner services (one-time)

Trusted Advisor has **two** App Runner services: **backend** (port 8080) and **frontend** (port 3000). The canonical CLI-only flow is in [deploy-apprunner-cli.md](deploy-apprunner-cli.md). Summary:

**CLI (recommended)**

1. **ECR access role:** Run `./scripts/iam-apprunner-ecr-role.sh`, then `export APP_RUNNER_ECR_ACCESS_ROLE=arn:aws:iam::YOUR_ACCOUNT_ID:role/AppRunnerECRAccess`.
2. **Backend:** `./scripts/create-apprunner-service-backend.sh us-east-1`. Wait until RUNNING. Push env: `./scripts/update-apprunner-env.sh .env.prod <BACKEND_ARN> us-east-1`.
3. **Frontend:** `./scripts/create-apprunner-service-frontend.sh us-east-1 'https://<BACKEND_APP_RUNNER_URL>'`. Wait until RUNNING. Push env: `./scripts/update-apprunner-env.sh .env.prod <FRONTEND_ARN> us-east-1`.
4. Set GitHub variables **APP_RUNNER_SERVICE_ARN_BACKEND** and **APP_RUNNER_SERVICE_ARN_FRONTEND** to the respective ARNs.

**Console (alternative)**

- **Backend:** Source = ECR `trusted-advisor-backend`, tag `latest`; port **8080**; health path `/health`; env vars from `.env.prod`. Deployment trigger = Manual.
- **Frontend:** Source = ECR `trusted-advisor-frontend`, tag `latest`; port **3000**; health = TCP or `/`; set `BACKEND_URL` and `NEXT_PUBLIC_BACKEND_URL` to the backend App Runner URL. Deployment trigger = Manual.

**Push config (env vars) from the command line**

```bash
# Backend
./scripts/update-apprunner-env.sh .env.prod <BACKEND_SERVICE_ARN> us-east-1

# Frontend
./scripts/update-apprunner-env.sh .env.prod <FRONTEND_SERVICE_ARN> us-east-1
```

Get ARNs: `aws apprunner list-services --region us-east-1 --query 'ServiceSummaryList[*].[ServiceName,ServiceArn]' --output table`. Wait for each deployment to complete (status RUNNING).

---

## 4. GitHub Actions setup

**Secrets** (Settings → Secrets and variables → Actions → Secrets):

- `AWS_ACCESS_KEY_ID` — access key of the IAM user above.
- `AWS_SECRET_ACCESS_KEY` — secret key.
- (Optional) `SLACK_WEBHOOK_URL` — for deploy notifications.

**Variables** (Settings → Secrets and variables → Actions → Variables, **repository** level):

- `ENABLE_AWS_DEPLOY` = `true`.
- `APP_RUNNER_SERVICE_ARN_BACKEND` = the **backend** Service ARN (e.g. `arn:aws:apprunner:us-east-1:YOUR_ACCOUNT:service/trusted-advisor-backend/xxxx`).
- `APP_RUNNER_SERVICE_ARN_FRONTEND` = the **frontend** Service ARN (e.g. `arn:aws:apprunner:us-east-1:YOUR_ACCOUNT:service/trusted-advisor-frontend/xxxx`).
- (Optional) `AWS_REGION` (e.g. `us-east-1`; default in workflow is `us-east-1`).

**If you see "Invalid Access Role in AuthenticationConfiguration" or "Failed to copy the image from ECR":** App Runner uses an IAM role to pull from ECR. That role must exist, have the correct **trust policy** (allow `apprunner.amazonaws.com` to assume it), and have ECR pull permissions. See [Fix ECR access role](#fix-ecr-access-role) below.

**If you see `exec format error` in App Runner logs:** App Runner only runs **linux/amd64**. That error means the image in ECR was built for another arch (e.g. arm64 on a Mac).

- **Fix 1 (recommended):** Push to **main** so CI runs. The workflow builds with `platforms: linux/amd64` and the Dockerfile uses `FROM --platform=linux/amd64`; it pushes to ECR and starts an App Runner deployment. After that run, the service will pull the new amd64 image.
- **Fix 2:** If you already pushed from this repo and still see the error, the **current** image in ECR may be an old arm64 one. In ECR → `trusted-advisor-backend` or `trusted-advisor-frontend` → delete the `latest` (and any other) tag(s) for the bad image, then push to main again so CI builds and pushes a fresh amd64 image and deploys.
- **Local build/push:** Always use `docker build --platform linux/amd64 ...` and push that image to ECR.

---

## 5. After that

- Push to **main**: CI builds both images, pushes to ECR, runs **App Runner** `start-deployment` for backend and frontend (if ARNs are set), waits for each operation.
- Set **FRONTEND_URL** on the **backend** service to the frontend App Runner URL. Set **BACKEND_URL** / **NEXT_PUBLIC_BACKEND_URL** on the **frontend** service to the backend App Runner URL. For OAuth, set redirect URIs in Google/GitHub to your frontend App Runner URL.

### Auth: avoid `[auth][warn][env-url-basepath-mismatch]`

Auth.js compares `AUTH_URL` / `NEXTAUTH_URL` with `basePath` (`/api/auth`). To avoid the warning:

- Set **NEXTAUTH_URL** (and optionally **AUTH_URL**) to the **app root only**: `https://<your-app-runner-domain>` — no path, or path `/`.
  Example: `https://fzece27dg2.us-east-1.awsapprunner.com`
- Or set it to the full base path: `https://<domain>/api/auth`
- Do **not** set a different path (e.g. `https://domain/auth`) or the warning will appear.
  The app normalizes a wrong path to the origin at runtime, but it’s better to set the correct URL in App Runner env and in your X app callback URL.

---

## Fix ECR access role

When deployments fail with **Invalid Access Role in AuthenticationConfiguration** or **Failed to copy the image from ECR**, the IAM role that App Runner uses to pull from ECR is missing or misconfigured.

1. **See which role the service uses**
   ```bash
   aws apprunner describe-service --service-arn YOUR_SERVICE_ARN --region us-east-1 \
     --query 'Service.SourceConfiguration.AuthenticationConfiguration.AccessRoleArn' --output text
   ```
   Example: `arn:aws:iam::YOUR_ACCOUNT_ID:role/AppRunnerECRAccess`

2. **Create or fix the role in IAM**
   - IAM → Roles → look for that role name (e.g. `AppRunnerECRAccess`). If it’s missing, create it; if it exists, open it to fix trust + permissions.
   - **Trust relationship:** Must allow App Runner to assume the role. Replace the role’s trust policy with:
     ```json
     {
       "Version": "2012-10-17",
       "Statement": [
         {
           "Effect": "Allow",
           "Principal": { "Service": "build.apprunner.amazonaws.com" },
           "Action": "sts:AssumeRole"
         }
         ]
     }
     ```
     (App Runner’s ECR pull uses the **build** service principal.)
   - **Permissions:** Attach the managed policy **AWSAppRunnerServicePolicyForECRAccess**, or an inline policy that allows:
     - `ecr:GetDownloadUrlForLayer`
     - `ecr:BatchGetImage`
     - `ecr:BatchCheckLayerAvailability`
     on resources `arn:aws:ecr:us-east-1:YOUR_ACCOUNT_ID:repository/trusted-advisor-backend` and `.../trusted-advisor-frontend` (and `*` for `GetAuthorizationToken` if you use a custom policy; ECR pull typically uses the repo ARNs).

3. **Redeploy**
   After saving the role, start a new deployment (no service update needed if the role ARN is unchanged):
   ```bash
   aws apprunner start-deployment --service-arn YOUR_SERVICE_ARN --region us-east-1
   ```

---

## Quick checklist

| Step | What | Done? |
|------|------|--------|
| 1 | IAM: ECR + App Runner permissions on deploy user | ☐ |
| 2 | ECR repos `trusted-advisor-backend` and `trusted-advisor-frontend` exist and have at least one image (e.g. `latest`) | ☐ |
| 3 | App Runner **backend** service created (port 8080, `/health`), status Running | ☐ |
| 4 | App Runner **frontend** service created (port 3000, BACKEND_URL set), status Running | ☐ |
| 5 | GitHub: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `ENABLE_AWS_DEPLOY=true`, `APP_RUNNER_SERVICE_ARN_BACKEND`, `APP_RUNNER_SERVICE_ARN_FRONTEND` | ☐ |
| 6 | Push to main and confirm “Deploy to AWS App Runner” workflow succeeds | ☐ |
