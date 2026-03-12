#!/usr/bin/env bash
# Generate create-service JSON for frontend and run aws apprunner create-service.
# Usage: ./scripts/create-apprunner-service-frontend.sh [region] [backend-url]
# Requires: APP_RUNNER_ECR_ACCESS_ROLE set (from iam-apprunner-ecr-role.sh).
# backend-url: e.g. https://xxxx.us-east-1.awsapprunner.com (from backend service). Use REPLACE_ME to set later.
# Output: apprunner-frontend-input.json (add to .gitignore); then runs create-service.

set -e

REGION="${1:-us-east-1}"
BACKEND_URL="${2:-REPLACE_ME}"
SERVICE_NAME="${APP_RUNNER_FRONTEND_NAME:-trusted-advisor-frontend}"
REPO_NAME="trusted-advisor-frontend"
IMAGE_TAG="${IMAGE_TAG:-latest}"

if [ -z "${APP_RUNNER_ECR_ACCESS_ROLE:-}" ]; then
  echo "Error: APP_RUNNER_ECR_ACCESS_ROLE is not set. Run iam-apprunner-ecr-role.sh first and export the ARN."
  exit 1
fi

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
IMAGE_ID="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${REPO_NAME}:${IMAGE_TAG}"

OUTPUT_JSON="apprunner-frontend-input.json"
if command -v jq >/dev/null 2>&1; then
  jq -n \
    --arg name "$SERVICE_NAME" \
    --arg image "$IMAGE_ID" \
    --arg role "$APP_RUNNER_ECR_ACCESS_ROLE" \
    --arg backend "$BACKEND_URL" \
    '{
      ServiceName: $name,
      SourceConfiguration: {
        ImageRepository: {
          ImageIdentifier: $image,
          ImageRepositoryType: "ECR",
          ImageConfiguration: {
            Port: "3000",
            RuntimeEnvironmentVariables: {
              NEXT_PUBLIC_BACKEND_URL: $backend,
              BACKEND_URL: $backend
            }
          }
        },
        AuthenticationConfiguration: { AccessRoleArn: $role },
        AutoDeploymentsEnabled: false
      },
      InstanceConfiguration: { Cpu: "1024", Memory: "2048" },
      HealthCheckConfiguration: {
        Protocol: "TCP",
        Interval: 10,
        Timeout: 5,
        HealthyThreshold: 1,
        UnhealthyThreshold: 5
      }
    }' > "$OUTPUT_JSON"
else
  cat > "$OUTPUT_JSON" << EOF
{
  "ServiceName": "$SERVICE_NAME",
  "SourceConfiguration": {
    "ImageRepository": {
      "ImageIdentifier": "$IMAGE_ID",
      "ImageRepositoryType": "ECR",
      "ImageConfiguration": {
        "Port": "3000",
        "RuntimeEnvironmentVariables": {
          "NEXT_PUBLIC_BACKEND_URL": "$BACKEND_URL",
          "BACKEND_URL": "$BACKEND_URL"
        }
      }
    },
    "AuthenticationConfiguration": {
      "AccessRoleArn": "$APP_RUNNER_ECR_ACCESS_ROLE"
    },
    "AutoDeploymentsEnabled": false
  },
  "InstanceConfiguration": { "Cpu": "1024", "Memory": "2048" },
  "HealthCheckConfiguration": {
    "Protocol": "TCP",
    "Interval": 10,
    "Timeout": 5,
    "HealthyThreshold": 1,
    "UnhealthyThreshold": 5
  }
}
EOF
fi

echo "Wrote $OUTPUT_JSON"
echo "Creating App Runner service (frontend)..."
aws apprunner create-service \
  --cli-input-json "file://${OUTPUT_JSON}" \
  --region "$REGION"

echo ""
echo "Wait until status is RUNNING. Then set GitHub variable APP_RUNNER_SERVICE_ARN_FRONTEND to the ServiceArn."
echo "If you used REPLACE_ME for backend URL, update the frontend service env with: ./scripts/update-apprunner-env.sh .env.prod <FRONTEND_ARN> $REGION"
echo "  (and ensure BACKEND_URL / NEXT_PUBLIC_BACKEND_URL point to your backend App Runner URL)"