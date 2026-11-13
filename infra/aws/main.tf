# Learning configuration only. Resources are created only when you run apply.
terraform {
  required_version = ">= 1.5, < 2.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = "pca-exam-analyze"
      ManagedBy = "Terraform"
      Purpose   = "learning"
    }
  }
}

# An isolated network: does not depend on an existing default VPC.
resource "aws_vpc" "app" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
}

resource "aws_subnet" "app" {
  vpc_id     = aws_vpc.app.id
  cidr_block = "10.42.1.0/24"
}

resource "aws_internet_gateway" "app" {
  vpc_id = aws_vpc.app.id
}

resource "aws_route_table" "app" {
  vpc_id = aws_vpc.app.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.app.id
  }
}

resource "aws_route_table_association" "app" {
  subnet_id      = aws_subnet.app.id
  route_table_id = aws_route_table.app.id
}

resource "aws_security_group" "app" {
  name_prefix = "pca-learning-"
  description = "Allow SSH and app access only from the learner IP"
  vpc_id      = aws_vpc.app.id

  ingress {
    description = "SSH from your public IP"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.allowed_ipv4_cidr]
  }

  ingress {
    description = "App access from your public IP"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [var.allowed_ipv4_cidr]
  }

  egress {
    description = "Outbound downloads and database connections"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# AWS maintains this regional parameter with the latest Amazon Linux 2023 AMI.
data "aws_ssm_parameter" "amazon_linux" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_instance" "app" {
  ami                         = data.aws_ssm_parameter.amazon_linux.value
  instance_type               = "t3.micro"
  subnet_id                   = aws_subnet.app.id
  vpc_security_group_ids      = [aws_security_group.app.id]
  associate_public_ip_address = true
  key_name                    = var.key_pair_name
  user_data_replace_on_change = true

  # Installs Docker only. Application deployment is a separate future step.
  user_data = <<-EOF
    #!/bin/bash
    set -euxo pipefail
    dnf install -y docker
    systemctl enable --now docker
  EOF

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_size           = 16
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  tags = {
    Name = "pca-learning-app"
  }

  depends_on = [aws_route_table_association.app]
}
