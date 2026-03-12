#!/usr/bin/env bash
# Generate create-service JSON for backend and run aws apprunner create-service.
# Usage: ./scripts/create-apprunner-service-backend.sh [region]
# Requires: APP_RUNNER_ECR_ACCESS_ROLE set (from iam-apprunner-ecr-role.sh).
# Output: apprunner-backend-input.json (add to .gitignore); then runs create-service.

set -e

REGION="${1:-us-east-1}"
SERVICE_NAME="${APP_RUNNER_BACKEND_NAME:-trusted-advisor-backend}"
REPO_NAME="trusted-advisor-backend"
IMAGE_TAG="${IMAGE_TAG:-latest}"

if [ -z "${APP_RUNNER_ECR_ACCESS_ROLE:-}" ]; then
  echo "Error: APP_RUNNER_ECR_ACCESS_ROLE is not set. Run iam-apprunner-ecr-role.sh first and export the ARN."
  exit 1
fi

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
IMAGE_ID="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${REPO_NAME}:${IMAGE_TAG}"

# Env vars: create with empty; then run ./scripts/update-apprunner-env.sh .env.prod <backend-arn> <region>
OUTPUT_JSON="apprunner-backend-input.json"
if command -v jq >/dev/null 2>&1; then
  jq -n \
    --arg name "$SERVICE_NAME" \
    --arg image "$IMAGE_ID" \
    --arg role "$APP_RUNNER_ECR_ACCESS_ROLE" \
    '{
      ServiceName: $name,
      SourceConfiguration: {
        ImageRepository: {
          ImageIdentifier: $image,
          ImageRepositoryType: "ECR",
          ImageConfiguration: {
            Port: "8080",
            RuntimeEnvironmentVariables: {}
          }
        },
        AuthenticationConfiguration: { AccessRoleArn: $role },
        AutoDeploymentsEnabled: false
      },
      InstanceConfiguration: { Cpu: "1024", Memory: "2048" },
      HealthCheckConfiguration: {
        Protocol: "HTTP",
        Path: "/health",
        Interval: 10,
        Timeout: 5,
        HealthyThreshold: 1,
        UnhealthyThreshold: 5
      }
    }' > "$OUTPUT_JSON"
else
  # No jq: write minimal JSON (no env from file)
  cat > "$OUTPUT_JSON" << EOF
{
  "ServiceName": "$SERVICE_NAME",
  "SourceConfiguration": {
    "ImageRepository": {
      "ImageIdentifier": "$IMAGE_ID",
      "ImageRepositoryType": "ECR",
      "ImageConfiguration": {
        "Port": "8080",
        "RuntimeEnvironmentVariables": {}
      }
    },
    "AuthenticationConfiguration": {
      "AccessRoleArn": "$APP_RUNNER_ECR_ACCESS_ROLE"
    },
    "AutoDeploymentsEnabled": false
  },
  "InstanceConfiguration": { "Cpu": "1024", "Memory": "2048" },
  "HealthCheckConfiguration": {
    "Protocol": "HTTP",
    "Path": "/health",
    "Interval": 10,
    "Timeout": 5,
    "HealthyThreshold": 1,
    "UnhealthyThreshold": 5
  }
}
EOF
fi

echo "Wrote $OUTPUT_JSON"
echo "Creating App Runner service (backend)..."
aws apprunner create-service \
  --cli-input-json "file://${OUTPUT_JSON}" \
  --region "$REGION"

echo ""
echo "Wait until status is RUNNING. Then:"
echo "  1. Get backend URL: aws apprunner describe-service --service-arn <BACKEND_ARN> --region $REGION --query 'Service.ServiceUrl' --output text"
echo "  2. Set GitHub variable APP_RUNNER_SERVICE_ARN_BACKEND to the ServiceArn"
echo "  3. Create frontend service with BACKEND_URL set to that URL"
