# Learning example: creates a local text file, not cloud infrastructure.
terraform {
  required_version = ">= 1.5, < 2.0"

  required_providers {
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
}

resource "local_file" "project_note" {
  filename = "${path.module}/generated/project-note.txt"
  content  = <<-EOT
    Project: PCA Exam Analyze
    This file is managed by Terraform for a local learning exercise.
    Application URL: http://localhost:8080
  EOT
}

output "created_file" {
  description = "Path of the text file managed by Terraform."
  value       = local_file.project_note.filename
}
