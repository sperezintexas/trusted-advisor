#!/usr/bin/env bash
# Push env vars from a file to an existing App Runner service (triggers a new deployment).
# Usage: ./scripts/update-apprunner-env.sh <env-file> <service-arn> [region]
# Example: ./scripts/update-apprunner-env.sh .env.prod arn:aws:apprunner:us-east-1:123:service/trusted-advisor-backend/abc123 us-east-1
# Requires: jq (to build env JSON from file). Skips lines that don't look like KEY=VALUE.

set -e

ENV_FILE="${1:?Usage: $0 <env-file> <service-arn> [region]}"
SERVICE_ARN="${2:?Usage: $0 <env-file> <service-arn> [region]}"
REGION="${3:-us-east-1}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Error: env file not found: $ENV_FILE"
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "Error: jq is required. Install with: brew install jq"
  exit 1
fi

# Build JSON object from env file (KEY=VALUE; value may contain =). Skip comments and empty.
# split("=") then .[0] and (.[1:]|join("=")) so values with = work (jq split has no limit).
ENV_JSON=$(awk -F= '/^[A-Za-z_][A-Za-z0-9_]*=/' "$ENV_FILE" | grep -v '^#' | while IFS= read -r line; do
  key="${line%%=*}"
  key="${key#export }"
  key="${key%"${key##*[! ]}"}"
  val="${line#*=}"
  val="${val%%#*}"
  val="${val%"${val##*[! ]}"}"
  [ -n "$key" ] && echo "$key=$val"
done | jq -R -s '
  [ split("\n")[] | select(length > 0) | split("=") | (.[0], (.[1:] | join("="))) ] as $pairs |
  reduce range(0; ($pairs | length); 2) as $i ({}; . + {($pairs[$i]): ($pairs[$i+1] // "")})
')

# Get current source configuration; replace only RuntimeEnvironmentVariables, keep Port etc.
CURRENT=$(aws apprunner describe-service --service-arn "$SERVICE_ARN" --region "$REGION" --query 'Service.SourceConfiguration' --output json)
NEW_SOURCE=$(echo "$CURRENT" | jq --argjson env "$ENV_JSON" '
  .ImageRepository.ImageConfiguration = ((.ImageRepository.ImageConfiguration // {}) | .RuntimeEnvironmentVariables = $env)
')
# update-service expects SourceConfiguration without the outer wrapper
aws apprunner update-service \
  --service-arn "$SERVICE_ARN" \
  --source-configuration "$NEW_SOURCE" \
  --region "$REGION"

echo "Update started. Wait for deployment to complete (status RUNNING)."
echo "Check: aws apprunner describe-service --service-arn $SERVICE_ARN --region $REGION --query 'Service.Status' --output text"
