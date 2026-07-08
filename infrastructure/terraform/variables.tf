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

variable "ssh_cidr" {
  description = "CIDR block allowed to access SSH. Use your public IP with /32."
  type        = string
}

variable "admin_ui_cidr" {
  description = "CIDR block allowed to access management UIs such as Grafana, Prometheus, Kafka UI, and Zipkin."
  type        = string
  default     = "0.0.0.0/0"
}

variable "rds_instance_class" {
  description = "Instance class for RDS PostgreSQL."
  type        = string
  default     = "db.t3.micro"
}

variable "rds_engine_version" {
  description = "PostgreSQL engine version for RDS."
  type        = string
  default     = "16.14"
}

variable "rds_allocated_storage" {
  description = "Allocated storage size for RDS PostgreSQL in GB."
  type        = number
  default     = 20
}

variable "rds_database_name" {
  description = "Initial database name created by RDS."
  type        = string
  default     = "kok"
}

variable "rds_master_username" {
  description = "Master username for RDS PostgreSQL."
  type        = string
  default     = "kokadmin"
}

variable "rds_master_password" {
  description = "Master password for RDS PostgreSQL. Set this in terraform.tfvars and do not commit it."
  type        = string
  sensitive   = true
}
