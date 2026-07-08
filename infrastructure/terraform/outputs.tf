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

