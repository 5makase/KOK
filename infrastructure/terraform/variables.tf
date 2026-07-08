variable "project_name" {
  description = "Project name used as a prefix for resource names."
  type        = string
  default     = "kok"
}

variable "environment" {
  description = "Deployment environment name."
  type        = string
  default     = "dev"
}

variable "aws_region" {
  description = "AWS region where resources are created."
  type        = string
  default     = "ap-northeast-2"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR block for the public subnet used by EC2 instances."
  type        = string
  default     = "10.0.1.0/24"
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets used by the RDS subnet group."
  type        = list(string)
  default     = ["10.0.11.0/24", "10.0.12.0/24"]
}
