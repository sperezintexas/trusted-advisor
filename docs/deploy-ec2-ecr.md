# Deploy on EC2 with ECR (no App Runner)

CI builds and pushes Docker images to AWS ECR on every push to `main`. Run the app on an EC2 instance by pulling those images and starting the containers.

## Prerequisites

- **GitHub**: Repo secrets `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (so CI can push to ECR).
- **EC2**: Instance with Docker installed, in the same AWS account/region as ECR.
- **ECR repos**: `trusted-advisor-backend`, `trusted-advisor-frontend` (CI creates them if missing).

## 1. ECR and CI

- Push to `main` → CI runs tests, then **Build and push to ECR** builds and pushes:
  - `trusted-advisor-backend:latest` (and `:<sha>`)
  - `trusted-advisor-frontend:latest` (and `:<sha>`)
- Region default: `us-east-1` (override with GitHub variable `AWS_REGION`).

## 2. On the EC2 instance

### Install Docker (if needed)

```bash
sudo yum update -y
sudo yum install -y docker
sudo systemctl start docker
sudo usermod -aG docker $USER
# Log out and back in, or: newgrp docker
```

### Log in to ECR

```bash
AWS_REGION=us-east-1
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com
```

### Pull and run (example with env file)

Create a `.env` on the server (or use Secrets Manager / Parameter Store) with at least:

- `MONGODB_URI` or `MONGODB_URI_B64`
- `MONGODB_DATABASE`
- `XAI_API_KEY`
- `AUTH_SECRET`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` (for login)
- `FRONTEND_URL` = your frontend URL (e.g. `https://your-domain.com`)

Then:

```bash
export REGISTRY=$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Backend (port 8080)
docker pull $REGISTRY/trusted-advisor-backend:latest
docker run -d --name backend -p 8080:8080 --env-file .env \
  $REGISTRY/trusted-advisor-backend:latest

# Frontend (port 3000); backend must be reachable at BACKEND_URL
docker pull $REGISTRY/trusted-advisor-frontend:latest
docker run -d --name frontend -p 3000:3000 \
  -e BACKEND_URL=http://localhost:8080 \
  --env-file .env \
  $REGISTRY/trusted-advisor-frontend:latest
```

If the browser hits the frontend on a different host (e.g. a domain), set `BACKEND_URL` to the **public** backend URL (e.g. `https://api.your-domain.com`) and set `FRONTEND_URL` in `.env` to that frontend URL so OAuth redirects work.

### Optional: docker-compose with ECR images

Create `docker-compose.prod.yml` (do not commit secrets):

```yaml
services:
  backend:
    image: ${ECR_REGISTRY}/trusted-advisor-backend:latest
    ports:
      - "8080:8080"
    env_file: .env
    restart: unless-stopped

  frontend:
    image: ${ECR_REGISTRY}/trusted-advisor-frontend:latest
    ports:
      - "3000:3000"
    environment:
      BACKEND_URL: http://backend:8080
    depends_on:
      - backend
    restart: unless-stopped
```

Run:

```bash
export ECR_REGISTRY=$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

## 3. Updating after a new push to main

```bash
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

docker pull $REGISTRY/trusted-advisor-backend:latest
docker pull $REGISTRY/trusted-advisor-frontend:latest

# Restart containers (names from your run/compose)
docker stop backend frontend
docker rm backend frontend
# Then run or compose up again as above
```

Or use a small script or systemd unit that pulls and restarts; optionally run it from a cron or trigger from CI (e.g. SSM Run Command, or a webhook on the instance).

## 4. Security and networking

- Restrict security group so 8080/3000 are only from a load balancer or CloudFront if you use one; do not expose both to 0.0.0.0 in production unless intended.
- Prefer HTTPS (ALB, CloudFront, or nginx on the instance) and set `FRONTEND_URL` / redirect URIs accordingly.
- Keep `.env` out of git and limit IAM permissions on the instance to what’s needed (ECR pull, optional Secrets Manager).
