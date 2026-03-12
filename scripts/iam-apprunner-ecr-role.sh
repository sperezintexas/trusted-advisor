#!/usr/bin/env bash
# Create IAM role for App Runner to pull images from ECR (one-time).
# Usage: ./scripts/iam-apprunner-ecr-role.sh [role-name]
# Default role name: AppRunnerECRAccess
# Requires: AWS CLI configured with permission to create roles and attach policies.
# Output: Role ARN to use as APP_RUNNER_ECR_ACCESS_ROLE when creating App Runner services.

set -e

ROLE_NAME="${1:-AppRunnerECRAccess}"
REGION="${AWS_REGION:-us-east-1}"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

echo "Creating IAM role: $ROLE_NAME (account: $ACCOUNT_ID)"

# Trust policy: allow App Runner build service to assume this role
TRUST_POLICY='{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Service": "build.apprunner.amazonaws.com" },
      "Action": "sts:AssumeRole"
    }
  ]
}'

if aws iam get-role --role-name "$ROLE_NAME" 2>/dev/null; then
  echo "Role $ROLE_NAME already exists."
else
  aws iam create-role \
    --role-name "$ROLE_NAME" \
    --assume-role-policy-document "$TRUST_POLICY" \
    --description "Allows App Runner to pull images from ECR for trusted-advisor"
  echo "Created role: $ROLE_NAME"
fi

# Attach managed policy for ECR read (required for private ECR)
aws iam attach-role-policy \
  --role-name "$ROLE_NAME" \
  --policy-arn "arn:aws:iam::aws:policy/service-role/AWSAppRunnerServicePolicyForECRAccess"

echo "Attached AWSAppRunnerServicePolicyForECRAccess."

ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${ROLE_NAME}"
echo ""
echo "Use this when creating App Runner services:"
echo "  export APP_RUNNER_ECR_ACCESS_ROLE=$ROLE_ARN"
echo ""
