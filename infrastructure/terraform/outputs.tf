output "vpc_id" {
  description = "Created VPC ID."
  value       = aws_vpc.main.id
}

output "public_subnet_id" {
  description = "Public subnet ID for EC2 instances."
  value       = aws_subnet.public.id
}

output "private_subnet_ids" {
  description = "Private subnet IDs for RDS."
  value       = aws_subnet.private[*].id
}

output "public_route_table_id" {
  description = "Public route table ID."
  value       = aws_route_table.public.id
}

output "private_route_table_id" {
  description = "Private route table ID."
  value       = aws_route_table.private.id
}

output "app_security_group_id" {
  description = "Security group ID for ec2-app."
  value       = aws_security_group.app.id
}

output "infra_security_group_id" {
  description = "Security group ID for ec2-infra."
  value       = aws_security_group.infra.id
}

output "monitoring_security_group_id" {
  description = "Security group ID for ec2-monitoring."
  value       = aws_security_group.monitoring.id
}

output "rds_security_group_id" {
  description = "Security group ID for RDS PostgreSQL."
  value       = aws_security_group.rds.id
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint."
  value       = aws_db_instance.postgres.endpoint
}

output "rds_address" {
  description = "RDS PostgreSQL hostname."
  value       = aws_db_instance.postgres.address
}

output "app_public_ip" {
  description = "Elastic IP address of ec2-app."
  value       = aws_eip.app.public_ip
}

output "infra_public_ip" {
  description = "Elastic IP address of ec2-infra."
  value       = aws_eip.infra.public_ip
}

output "monitoring_public_ip" {
  description = "Elastic IP address of ec2-monitoring."
  value       = aws_eip.monitoring.public_ip
}

output "app_private_ip" {
  description = "Private IP address of ec2-app."
  value       = aws_instance.app.private_ip
}

output "infra_private_ip" {
  description = "Private IP address of ec2-infra."
  value       = aws_instance.infra.private_ip
}

output "monitoring_private_ip" {
  description = "Private IP address of ec2-monitoring."
  value       = aws_instance.monitoring.private_ip
}
