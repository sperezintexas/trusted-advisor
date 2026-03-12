# Deploy on EC2 with ECR (or use App Runner)

CI builds and pushes Docker images to AWS ECR on every push to `main`. You can either run the app on **EC2** (this doc) or use **App Runner** (see below).

**App Runner:** To deploy with AWS App Runner like your other app, do the **one-time CLI setup** in **`docs/deploy-apprunner-cli.md`** (ECR, IAM role, create both services, push env vars). Then set GitHub variables `ENABLE_AWS_DEPLOY=true`, `APP_RUNNER_SERVICE_ARN_BACKEND`, and `APP_RUNNER_SERVICE_ARN_FRONTEND`. The workflow `.github/workflows/deploy-aws-apprunner.yml` runs after CI and triggers App Runner deployment.

---

## EC2 option

Run the app on an EC2 instance by pulling those images and starting the containers.

## Prerequisites

- **GitHub**: Repo secrets `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (so CI can push to ECR).
- **EC2**: Instance with Docker installed, in the same AWS account/region as ECR.
- **ECR repos**: `trusted-advisor-backend`, `trusted-advisor-frontend` (CI creates them if missing).

## 0. One-time AWS setup (from your machine)

From the repo root, with AWS CLI configured:

```bash
# Create ECR repos (default region us-east-1)
./scripts/aws-ecr-setup.sh

# Optional: launch an EC2 instance with Docker + ECR login in user-data
# Set KEY_NAME (and optionally SUBNET_ID, SECURITY_GROUP_IDS, IAM_INSTANCE_PROFILE) then:
./scripts/aws-ec2-launch.sh
```

- `aws-ecr-setup.sh [region]` — creates `trusted-advisor-backend` and `trusted-advisor-frontend` in ECR.
- `aws-ec2-launch.sh [region] [ami-id]` — runs `ec2 run-instances` with user-data that installs Docker and logs in to ECR. The instance needs an IAM instance profile with ECR read (e.g. `AmazonEC2ContainerRegistryReadOnly`). Set `IAM_INSTANCE_PROFILE=YourProfileName` before running to attach it at launch.

### Where to get KEY_NAME, IAM_INSTANCE_PROFILE, SECURITY_GROUP_IDS, SUBNET_ID

Use the same **region** everywhere (e.g. `us-east-1`). Set `AWS_REGION=us-east-1` or pass it to the CLI.

| Variable | What it is | How to get or create it |
|----------|------------|--------------------------|
| **KEY_NAME** | EC2 key pair name for SSH | **Create:** EC2 → Key Pairs → Create key pair → name it (e.g. `trusted-advisor-key`). Save the `.pem` file. **List:** `aws ec2 describe-key-pairs --region us-east-1 --query 'KeyPairs[*].KeyName' --output text` |
| **IAM_INSTANCE_PROFILE** | IAM role name attached to the instance (so it can pull from ECR) | **Create:** IAM → Roles → Create role → Trusted entity: AWS service → Use case: EC2 → Next → Attach policy **AmazonEC2ContainerRegistryReadOnly** → Name (e.g. `trusted-advisor-ec2-ecr`) → Create. **List:** `aws iam list-instance-profiles --query 'InstanceProfiles[*].InstanceProfileName' --output text` |
| **SECURITY_GROUP_IDS** | Security group ID(s); open 22 (SSH), 3000 (frontend), 8080 (backend), and optionally 80/443 | **Create:** VPC → Security groups → Create → e.g. name `trusted-advisor-sg`, add inbound: SSH 22, Custom TCP 3000, 8080 (from your IP or 0.0.0.0/0 for testing). **Get ID:** `aws ec2 describe-security-groups --region us-east-1 --filters "Name=group-name,Values=trusted-advisor-sg" --query 'SecurityGroups[0].GroupId' --output text` (or use default VPC’s default SG and get its ID the same way). |
| **SUBNET_ID** | Subnet to launch the instance in (optional; default VPC is used if omitted) | **List:** `aws ec2 describe-subnets --region us-east-1 --query 'Subnets[*].[SubnetId,AvailabilityZone,CidrBlock]' --output table`. Pick a public subnet if you want a public IP. **One ID:** `aws ec2 describe-subnets --region us-east-1 --query 'Subnets[0].SubnetId' --output text` |

**Minimal export block (replace placeholders):**

```bash
export AWS_REGION=us-east-1
export KEY_NAME=your-key-pair-name                    # from Key Pairs in EC2
export IAM_INSTANCE_PROFILE=trusted-advisor-ec2-ecr   # IAM role with ECR read
export SECURITY_GROUP_IDS=sg-0123456789abcdef0        # security group ID
# export SUBNET_ID=subnet-xxx                         # optional; omit to use default
```

Then run `./scripts/aws-ec2-launch.sh $AWS_REGION`.

**Quick hint:** Run `./scripts/aws-ec2-env.sh us-east-1` to print export lines and see existing key pairs, instance profiles, and security groups in your account so you can fill in real values.

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
