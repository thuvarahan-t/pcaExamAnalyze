variable "aws_region" {
  description = "AWS region for this independent learning environment."
  type        = string
  default     = "ap-south-1"
}

variable "allowed_ipv4_cidr" {
  description = "Your public IPv4 address with /32, for example 203.0.113.10/32."
  type        = string

  validation {
    condition     = can(cidrnetmask(var.allowed_ipv4_cidr)) && can(regex("/32$", var.allowed_ipv4_cidr))
    error_message = "Provide a single IPv4 address with /32; do not open access to the entire internet."
  }
}

variable "key_pair_name" {
  description = "Name of an existing EC2 SSH key pair in the selected AWS region. Never put the private key here."
  type        = string
}
