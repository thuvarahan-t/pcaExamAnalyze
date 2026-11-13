output "instance_id" {
  description = "EC2 instance managed by this configuration."
  value       = aws_instance.app.id
}

output "public_ip" {
  description = "Public IP; it may change after a stop/start."
  value       = aws_instance.app.public_ip
}

output "ssh_command" {
  description = "Add -i followed by your local private-key path when connecting."
  value       = "ssh ec2-user@${aws_instance.app.public_ip}"
}
