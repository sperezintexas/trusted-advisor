#!/usr/bin/env bash
# Create ECR repositories for trusted-advisor (backend + frontend).
# Usage: ./scripts/aws-ecr-setup.sh [region]
# Requires: aws CLI configured (aws sts get-caller-identity works).

set -e

REGION="${1:-us-east-1}"
BACKEND_REPO="trusted-advisor-backend"
FRONTEND_REPO="trusted-advisor-frontend"

echo "Using region: $REGION"
aws sts get-caller-identity --query Account --output text > /dev/null || { echo "AWS CLI not configured or no permission"; exit 1; }

for REPO in "$BACKEND_REPO" "$FRONTEND_REPO"; do
  if aws ecr describe-repositories --repository-names "$REPO" --region "$REGION" 2>/dev/null; then
    echo "ECR repo $REPO already exists."
  else
    aws ecr create-repository --repository-name "$REPO" --region "$REGION"
    echo "Created ECR repo: $REPO"
  fi
done

echo ""
echo "Done. Repos: $BACKEND_REPO, $FRONTEND_REPO"
echo "Registry: $(aws sts get-caller-identity --query Account --output text).dkr.ecr.$REGION.amazonaws.com"
echo "GitHub: set secrets AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY and variable AWS_REGION=$REGION (optional)."
