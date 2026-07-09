resource "aws_security_group" "app" {
  name        = "${local.name_prefix}-sg-app"
  description = "Security group for app EC2 instance"
  vpc_id      = aws_vpc.main.id

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-sg-app"
  })
}

resource "aws_security_group" "infra" {
  name        = "${local.name_prefix}-sg-infra"
  description = "Security group for infra EC2 instance"
  vpc_id      = aws_vpc.main.id

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-sg-infra"
  })
}

resource "aws_security_group" "monitoring" {
  name        = "${local.name_prefix}-sg-monitoring"
  description = "Security group for monitoring EC2 instance"
  vpc_id      = aws_vpc.main.id

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-sg-monitoring"
  })
}

resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-sg-rds"
  description = "Security group for RDS PostgreSQL"
  vpc_id      = aws_vpc.main.id

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-sg-rds"
  })
}

# ec2-app inbound
resource "aws_vpc_security_group_ingress_rule" "app_http" {
  security_group_id = aws_security_group.app.id
  description       = "HTTP access to Nginx"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "app_ssh" {
  security_group_id = aws_security_group.app.id
  description       = "SSH access from admin IP"
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
  cidr_ipv4         = var.ssh_cidr
}

resource "aws_vpc_security_group_ingress_rule" "app_actuator_services" {
  security_group_id            = aws_security_group.app.id
  description                  = "Prometheus scrape for gateway and domain services"
  ip_protocol                  = "tcp"
  from_port                    = 8000
  to_port                      = 8007
  referenced_security_group_id = aws_security_group.monitoring.id
}

resource "aws_vpc_security_group_ingress_rule" "app_eureka" {
  security_group_id            = aws_security_group.app.id
  description                  = "Eureka access from monitoring"
  ip_protocol                  = "tcp"
  from_port                    = 8761
  to_port                      = 8761
  referenced_security_group_id = aws_security_group.monitoring.id
}

resource "aws_vpc_security_group_ingress_rule" "app_ai_ops" {
  security_group_id            = aws_security_group.app.id
  description                  = "AI Ops metrics access from monitoring"
  ip_protocol                  = "tcp"
  from_port                    = 8099
  to_port                      = 8099
  referenced_security_group_id = aws_security_group.monitoring.id
}

# ec2-infra inbound
resource "aws_vpc_security_group_ingress_rule" "infra_ssh" {
  security_group_id = aws_security_group.infra.id
  description       = "SSH access from admin IP"
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
  cidr_ipv4         = var.ssh_cidr
}

resource "aws_vpc_security_group_ingress_rule" "infra_kafka" {
  security_group_id            = aws_security_group.infra.id
  description                  = "Kafka access from app services"
  ip_protocol                  = "tcp"
  from_port                    = 9092
  to_port                      = 9092
  referenced_security_group_id = aws_security_group.app.id
}

resource "aws_vpc_security_group_ingress_rule" "infra_redis" {
  security_group_id            = aws_security_group.infra.id
  description                  = "Redis access from app services"
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
  referenced_security_group_id = aws_security_group.app.id
}

resource "aws_vpc_security_group_ingress_rule" "infra_kafka_ui" {
  security_group_id = aws_security_group.infra.id
  description       = "Kafka UI access"
  ip_protocol       = "tcp"
  from_port         = 8080
  to_port           = 8080
  cidr_ipv4         = var.admin_ui_cidr
}

resource "aws_vpc_security_group_ingress_rule" "infra_kafka_exporter" {
  security_group_id            = aws_security_group.infra.id
  description                  = "Kafka exporter scrape from Prometheus"
  ip_protocol                  = "tcp"
  from_port                    = 9308
  to_port                      = 9308
  referenced_security_group_id = aws_security_group.monitoring.id
}

resource "aws_vpc_security_group_ingress_rule" "infra_redis_exporter" {
  security_group_id            = aws_security_group.infra.id
  description                  = "Redis exporter scrape from Prometheus"
  ip_protocol                  = "tcp"
  from_port                    = 9121
  to_port                      = 9121
  referenced_security_group_id = aws_security_group.monitoring.id
}

# ec2-monitoring inbound
resource "aws_vpc_security_group_ingress_rule" "monitoring_ssh" {
  security_group_id = aws_security_group.monitoring.id
  description       = "SSH access from admin IP"
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
  cidr_ipv4         = var.ssh_cidr
}

resource "aws_vpc_security_group_ingress_rule" "monitoring_grafana" {
  security_group_id = aws_security_group.monitoring.id
  description       = "Grafana access"
  ip_protocol       = "tcp"
  from_port         = 3000
  to_port           = 3000
  cidr_ipv4         = var.admin_ui_cidr
}

resource "aws_vpc_security_group_ingress_rule" "monitoring_prometheus" {
  security_group_id = aws_security_group.monitoring.id
  description       = "Prometheus access"
  ip_protocol       = "tcp"
  from_port         = 9090
  to_port           = 9090
  cidr_ipv4         = var.admin_ui_cidr
}

resource "aws_vpc_security_group_ingress_rule" "monitoring_zipkin_admin" {
  security_group_id = aws_security_group.monitoring.id
  description       = "Zipkin UI access"
  ip_protocol       = "tcp"
  from_port         = 9411
  to_port           = 9411
  cidr_ipv4         = var.admin_ui_cidr
}

resource "aws_vpc_security_group_ingress_rule" "monitoring_zipkin_from_app" {
  security_group_id            = aws_security_group.monitoring.id
  description                  = "Zipkin trace ingestion from app services"
  ip_protocol                  = "tcp"
  from_port                    = 9411
  to_port                      = 9411
  referenced_security_group_id = aws_security_group.app.id
}

resource "aws_vpc_security_group_ingress_rule" "monitoring_loki_from_app" {
  security_group_id            = aws_security_group.monitoring.id
  description                  = "Loki log ingestion from app Promtail"
  ip_protocol                  = "tcp"
  from_port                    = 3100
  to_port                      = 3100
  referenced_security_group_id = aws_security_group.app.id
}

resource "aws_vpc_security_group_ingress_rule" "monitoring_loki_from_infra" {
  security_group_id            = aws_security_group.monitoring.id
  description                  = "Loki log ingestion from infra Promtail"
  ip_protocol                  = "tcp"
  from_port                    = 3100
  to_port                      = 3100
  referenced_security_group_id = aws_security_group.infra.id
}

# RDS inbound
resource "aws_vpc_security_group_ingress_rule" "rds_postgres_from_app" {
  security_group_id            = aws_security_group.rds.id
  description                  = "PostgreSQL access from app services"
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = aws_security_group.app.id
}

# Outbound: keep EC2 egress open for package installs, image pulls, Slack, and managed AWS APIs.
resource "aws_vpc_security_group_egress_rule" "app_all" {
  security_group_id = aws_security_group.app.id
  description       = "Allow all outbound traffic"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "infra_all" {
  security_group_id = aws_security_group.infra.id
  description       = "Allow all outbound traffic"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "monitoring_all" {
  security_group_id = aws_security_group.monitoring.id
  description       = "Allow all outbound traffic"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "rds_all" {
  security_group_id = aws_security_group.rds.id
  description       = "Allow all outbound traffic"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}
