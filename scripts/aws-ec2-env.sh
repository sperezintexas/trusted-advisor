#!/usr/bin/env bash
# Print export commands for KEY_NAME, IAM_INSTANCE_PROFILE, SECURITY_GROUP_IDS, SUBNET_ID.
# Usage: ./scripts/aws-ec2-env.sh [region]
# Copy the output and fix any empty or placeholder values, then run aws-ec2-launch.sh.

set -e

REGION="${1:-us-east-1}"

echo "# Region (required)"
echo "export AWS_REGION=$REGION"
echo ""

echo "# SSH key pair name (required for SSH). Create in EC2 → Key Pairs if needed."
KEY_NAMES=$(aws ec2 describe-key-pairs --region "$REGION" --query 'KeyPairs[*].KeyName' --output text 2>/dev/null || true)
if [ -n "$KEY_NAMES" ]; then
  echo "# Available key pairs: $KEY_NAMES"
  echo "export KEY_NAME=REPLACE_WITH_ONE_ABOVE"
else
  echo "export KEY_NAME=your-key-pair-name"
fi
echo ""

echo "# IAM instance profile (required for ECR pull). Create in IAM → Roles (EC2, attach AmazonEC2ContainerRegistryReadOnly)."
PROFILES=$(aws iam list-instance-profiles --query 'InstanceProfiles[*].InstanceProfileName' --output text 2>/dev/null || true)
if [ -n "$PROFILES" ]; then
  echo "# Available instance profiles: $PROFILES"
  echo "export IAM_INSTANCE_PROFILE=REPLACE_WITH_ONE_ABOVE"
else
  echo "export IAM_INSTANCE_PROFILE=YourEC2RoleName"
fi
echo ""

echo "# Security group ID (optional). Must allow inbound: 22, 3000, 8080."
SGS=$(aws ec2 describe-security-groups --region "$REGION" --query 'SecurityGroups[*].[GroupId,GroupName]' --output text 2>/dev/null | head -5 || true)
if [ -n "$SGS" ]; then
  echo "# Example security groups (GroupId GroupName):"
  echo "# $SGS"
  echo "export SECURITY_GROUP_IDS=sg-xxxxxxxx"
else
  echo "export SECURITY_GROUP_IDS=sg-xxxxxxxx"
fi
echo ""

echo "# Subnet ID (optional). Omit to use default."
SUBNETS=$(aws ec2 describe-subnets --region "$REGION" --query 'Subnets[*].[SubnetId,AvailabilityZone]' --output text 2>/dev/null | head -3 || true)
if [ -n "$SUBNETS" ]; then
  echo "# Example subnets (SubnetId AZ):"
  echo "# $SUBNETS"
  echo "# export SUBNET_ID=subnet-xxxxxxxx"
else
  echo "# export SUBNET_ID=subnet-xxxxxxxx"
fi
