#!/usr/bin/env bash
# Build backend and frontend for linux/amd64 and push to ECR (for App Runner or EC2).
# Run from repo root. Requires: Docker, AWS CLI configured, ECR repos exist (aws-ecr-setup.sh).
# Usage: ./scripts/aws-ecr-push-local.sh [region] [backend-url]

set -e

REGION="${1:-${AWS_REGION:-us-east-1}}"
BACKEND_URL_FOR_FRONTEND="${2:-${BACKEND_URL:-${NEXT_PUBLIC_BACKEND_URL:-}}}"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

if [ -z "${BACKEND_URL_FOR_FRONTEND}" ]; then
  echo "Error: backend URL is required for frontend build."
  echo "Set BACKEND_URL (or NEXT_PUBLIC_BACKEND_URL), or pass it as arg #2."
  echo "Example: ./scripts/aws-ecr-push-local.sh us-east-1 https://<backend>.awsapprunner.com"
  exit 1
fi

echo "Region: $REGION  Registry: $REGISTRY"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"

echo ""
echo "Building backend (linux/amd64)..."
docker build --platform linux/amd64 -f Dockerfile.backend -t trusted-advisor-backend:latest .
docker tag trusted-advisor-backend:latest "$REGISTRY/trusted-advisor-backend:latest"
docker push "$REGISTRY/trusted-advisor-backend:latest"
echo "Pushed trusted-advisor-backend:latest"

echo ""
echo "Building frontend (linux/amd64)..."
docker build \
  --platform linux/amd64 \
  --build-arg BACKEND_URL="$BACKEND_URL_FOR_FRONTEND" \
  --build-arg NEXT_PUBLIC_BACKEND_URL="$BACKEND_URL_FOR_FRONTEND" \
  -f frontend/Dockerfile \
  -t trusted-advisor-frontend:latest \
  ./frontend
docker tag trusted-advisor-frontend:latest "$REGISTRY/trusted-advisor-frontend:latest"
docker push "$REGISTRY/trusted-advisor-frontend:latest"
echo "Pushed trusted-advisor-frontend:latest"

echo ""
echo "Done. Both images are in ECR. Continue with step 4 in docs/deploy-apprunner-cli.md (create backend App Runner service)."
