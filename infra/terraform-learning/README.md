# Terraform local learning example

This exercise manages one local text file. It does not deploy the application,
modify Docker containers, or change Render infrastructure. No cloud account is needed.

Install Terraform CLI before running these commands from the project root:

```powershell
cd infra/terraform-learning
terraform init
terraform fmt -check
terraform validate
terraform plan
terraform apply
```

- `init`: downloads the local provider and creates `.terraform.lock.hcl`.
- `fmt -check`: checks Terraform formatting.
- `validate`: checks the configuration.
- `plan`: previews the changes without creating the file.
- `apply`: asks for confirmation, then creates `generated/project-note.txt`.

Keep `.terraform.lock.hcl` in Git once generated. Local state, provider downloads,
and generated files are ignored by Git.

Run `terraform plan` again after applying: an unchanged configuration should report
no changes. Change the content in `main.tf` and run `plan` to preview an update.

To remove only the file managed by this exercise:

```powershell
terraform destroy
```

Review the plan and type `yes` to confirm. Keep the state file until cleanup is complete.
