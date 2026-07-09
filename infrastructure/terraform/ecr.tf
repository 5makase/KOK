locals {
  ecr_repository_names = [
    "kok-eureka-server",
    "kok-api-gateway",
    "kok-user-service",
    "kok-store-service",
    "kok-reservation-service",
    "kok-waiting-service",
    "kok-notification-service",
    "kok-review-service",
    "kok-payment-service",
    "kok-ai-ops-assistant",
  ]
}

resource "aws_ecr_repository" "service" {
  for_each = toset(local.ecr_repository_names)

  name                 = each.value
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = merge(local.common_tags, {
    Name = each.value
  })
}

resource "aws_ecr_lifecycle_policy" "service" {
  for_each = aws_ecr_repository.service

  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep the latest 10 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
