#!/usr/bin/env bash
set -euo pipefail

AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-570638873326}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
AWS_PROFILE="${AWS_PROFILE:-kok-deploy}"
IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD)}"
REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
PLATFORM="${PLATFORM:-linux/amd64}"
TARGET_SERVICE="${1:-}"

SERVICES=(
  "kok-eureka-server|:infrastructure:eureka-server:bootJar|infrastructure/eureka-server/build/libs"
  "kok-api-gateway|:infrastructure:api-gateway:bootJar|infrastructure/api-gateway/build/libs"
  "kok-user-service|:services:user-service:bootJar|services/user-service/build/libs"
  "kok-store-service|:services:store-service:bootJar|services/store-service/build/libs"
  "kok-reservation-service|:services:reservation-service:bootJar|services/reservation-service/build/libs"
  "kok-waiting-service|:services:waiting-service:bootJar|services/waiting-service/build/libs"
  "kok-notification-service|:services:notification-service:bootJar|services/notification-service/build/libs"
  "kok-review-service|:services:review-service:bootJar|services/review-service/build/libs"
  "kok-payment-service|:services:payment-service:bootJar|services/payment-service/build/libs"
  "kok-ai-ops-assistant|:infrastructure:ai-ops-assistant:bootJar|infrastructure/ai-ops-assistant/build/libs"
)

aws ecr get-login-password \
  --region "${AWS_REGION}" \
  --profile "${AWS_PROFILE}" \
  | docker login --username AWS --password-stdin "${REGISTRY}"

for service in "${SERVICES[@]}"; do
  IFS='|' read -r repository gradle_task jar_dir <<< "${service}"

  if [[ -n "${TARGET_SERVICE}" && "${TARGET_SERVICE}" != "${repository}" ]]; then
    continue
  fi

  echo "Building and pushing ${repository}:${IMAGE_TAG}"

  tags=(
    --tag "${REGISTRY}/${repository}:latest"
    --tag "${REGISTRY}/${repository}:${IMAGE_TAG}"
  )

  docker buildx build \
    --platform "${PLATFORM}" \
    --file deploy/app/Dockerfile \
    --build-arg "GRADLE_TASK=${gradle_task}" \
    --build-arg "JAR_DIR=${jar_dir}" \
    "${tags[@]}" \
    --push \
    .
done
