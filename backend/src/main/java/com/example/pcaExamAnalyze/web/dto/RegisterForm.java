package com.example.pcaExamAnalyze.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterForm {

    @NotBlank(message = "Username is required")
    @Pattern(regexp = "^[a-z0-9]{3,30}$",
            message = "Use 3-30 characters: lowercase letters and numbers only")
    private String username;

    @NotBlank(message = "Full name is required")
    @Size(max = 120)
    private String fullName;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^\\+94\\s(?:7\\d)\\s\\d{3}\\s\\d{4}$",
            message = "Enter a valid WhatsApp number like +94 74 104 6208")
    private String mobile;

    @NotBlank(message = "NIC is required")
    @Pattern(regexp = "^\\d{12}$",
            message = "Enter your 12 digit new NIC number")
    private String nic;

    @NotBlank(message = "District is required")
    private String district;

    @NotBlank(message = "Batch is required")
    @Pattern(regexp = "^(2026 Repeat Batch|2026 Proper Batch|2027 Batch)$",
            message = "Choose a valid batch")
    private String batch;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getNic() { return nic; }
    public void setNic(String nic) { this.nic = nic; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }
}
