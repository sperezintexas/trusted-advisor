#!/usr/bin/env bash
# Build backend and frontend for linux/amd64 and push to ECR (for App Runner or EC2).
# Run from repo root. Requires: Docker, AWS CLI configured, ECR repos exist (aws-ecr-setup.sh).
# Usage: ./scripts/aws-ecr-push-local.sh [region]

set -e

REGION="${1:-${AWS_REGION:-us-east-1}}"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

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
docker build --platform linux/amd64 -f frontend/Dockerfile -t trusted-advisor-frontend:latest ./frontend
docker tag trusted-advisor-frontend:latest "$REGISTRY/trusted-advisor-frontend:latest"
docker push "$REGISTRY/trusted-advisor-frontend:latest"
echo "Pushed trusted-advisor-frontend:latest"

echo ""
echo "Done. Both images are in ECR. Continue with step 4 in docs/deploy-apprunner-cli.md (create backend App Runner service)."
