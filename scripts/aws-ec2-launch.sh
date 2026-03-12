#!/usr/bin/env bash
# Launch an EC2 instance with Docker and ECR login in user-data.
# Usage: ./scripts/aws-ec2-launch.sh [region] [ami-id]
# Optional: set SUBNET_ID, SECURITY_GROUP_IDS, KEY_NAME for your VPC.
# Default: Amazon Linux 2023 in us-east-1, t3.micro.
# After launch: SSH in and run docker compose -f docker-compose.ecr.yml up -d (see docs/deploy-ec2-ecr.md).

set -e

REGION="${1:-us-east-1}"
# Amazon Linux 2023 (us-east-1, x86) - update if different region/arch
AMI_ID="${2:-$(aws ec2 describe-images --region "$REGION" --owners amazon --filters "Name=name,Values=al2023-ami-*-kernel-x86_64" "Name=state,Values=available" --query 'sort_by(Images,&CreationDate)[-1].ImageId' --output text 2>/dev/null || echo "")}"

if [ -z "$AMI_ID" ]; then
  echo "Could not resolve AMI. Pass AMI_ID as second arg, e.g.: $0 $REGION ami-0abc123"
  exit 1
fi

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGISTRY="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"

USERDATA=$(cat <<EOF
#!/bin/bash
set -e
yum update -y
yum install -y docker
systemctl start docker
systemctl enable docker
usermod -aG docker ec2-user
# ECR login (instance role must allow ecr:GetAuthorizationToken + ecr:BatchGetImage, etc.)
aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $REGISTRY
echo "Docker and ECR login ready. Pull images and run: ECR_REGISTRY=$REGISTRY docker compose -f docker-compose.ecr.yml up -d"
EOF
)

OPTS=(
  --region "$REGION"
  --image-id "$AMI_ID"
  --instance-type "t3.micro"
  --user-data "$USERDATA"
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=trusted-advisor}]"
  --associate-public-ip-address
)

# Optional: use your subnet and security group (e.g. open 22, 80, 443, 3000, 8080)
if [ -n "$SUBNET_ID" ]; then OPTS+=(--subnet-id "$SUBNET_ID"); fi
if [ -n "$SECURITY_GROUP_IDS" ]; then OPTS+=(--security-group-ids $SECURITY_GROUP_IDS); fi
if [ -n "$KEY_NAME" ]; then OPTS+=(--key-name "$KEY_NAME"); fi

# IAM instance profile so user-data can run: aws ecr get-login-password
if [ -n "$IAM_INSTANCE_PROFILE" ]; then OPTS+=(--iam-instance-profile "Name=$IAM_INSTANCE_PROFILE"); fi

echo "Launching instance (AMI=$AMI_ID, region=$REGION)..."
aws ec2 run-instances "${OPTS[@]}"

echo ""
echo "Instance launched. Ensure it has an IAM instance profile with ECR read (e.g. AmazonEC2ContainerRegistryReadOnly)."
echo "Set IAM_INSTANCE_PROFILE=YourProfileName and re-run to attach at launch. See docs/deploy-ec2-ecr.md."
