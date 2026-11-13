# AWS Terraform learning configuration

Code for a future EC2 environment. Saving or committing these files does not
deploy anything and does not change the existing Render service.

## What this describes

- An independent VPC, subnet, internet gateway, and public route.
- A security group allowing SSH (22) and the application port (8080) only from your IPv4 /32.
- An Amazon Linux 2023 x86-64 `t3.micro` EC2 instance with an encrypted 16 GiB gp3 disk.
- A startup script installing and starting Docker.

This is a learning server foundation, not a complete application deployment.
The script does not install Compose, copy the project, build an image, run the
app, configure Supabase, or configure a domain/HTTPS. Port 8080 has no application
listening until a later deployment step. It does not reproduce Render's CPU/RAM limits.

## Learn and validate without deploying

From the repository root, after installing Terraform:

```powershell
cd infra/aws
terraform init -backend=false
terraform fmt -check
terraform validate
```

Initialization downloads the AWS provider and creates `.terraform.lock.hcl`.
Commit that lock file after initialization. These commands do not create AWS resources
and do not need AWS credentials. Never commit credentials, private keys, or state.

## Future deployment, only when you decide to use AWS

1. Configure AWS credentials outside the code, preferably using AWS CLI SSO.
2. Ensure you have an EC2 SSH key pair in the selected region and its private key locally.
3. Copy `terraform.tfvars.example` to `terraform.tfvars`. Replace the example IP and key pair name.
4. Run `terraform plan` to inspect changes. This queries AWS and needs credentials.
5. Run `terraform apply` only when you intend to create the listed resources and accept AWS charges.

EC2, EBS storage, public IPv4, and data transfer may incur charges. No free-tier
eligibility is assumed. The server is a learning example without production backups
or HTTPS. Destroying it deletes its root disk; do not store the only copy of app data there.

To remove this AWS environment later, run `terraform destroy` in this directory
and review the confirmation. Preserve local state until cleanup is complete.
Render infrastructure is not managed by this configuration.
