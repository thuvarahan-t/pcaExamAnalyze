package com.example.pcaExamAnalyze.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Passwordless student profile kept in the server session for MCQ form autofill. */
@Getter
@Setter
@NoArgsConstructor
public class ExamStudentDetailsForm implements Serializable {

    @NotBlank(message = "Email address is required")
    @Email(message = "Enter a valid email address")
    @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$",
            message = "Enter a valid email address")
    @Size(max = 160, message = "Email address is too long")
    private String email;

    @NotBlank(message = "Registration ID is required")
    @Pattern(regexp = "^[A-Za-z0-9-]{3,30}$",
            message = "Use 3–30 letters, numbers, or hyphens")
    private String registrationId;

    @NotBlank(message = "NIC number is required")
    @Pattern(regexp = "^[0-9]{12}$",
            message = "NIC must contain exactly 12 digits")
    private String nic;

    @NotBlank(message = "Full name is required")
    @Size(min = 3, max = 150, message = "Full name must contain 3–150 characters")
    @Pattern(regexp = "^[\\p{L} .'-]+$",
            message = "Full name can contain letters only")
    private String fullName;

    @NotBlank(message = "Select a batch")
    private String batch;

    @NotBlank(message = "School is required")
    @Size(min = 2, max = 200, message = "School must contain 2–200 characters")
    private String school;

    @NotBlank(message = "Select a stream")
    private String stream;

    @NotBlank(message = "Select a district")
    private String district;

    public void normalize() {
        email = clean(email).toLowerCase();
        registrationId = clean(registrationId).toUpperCase();
        nic = clean(nic).replace(" ", "").toUpperCase();
        fullName = toTitleCase(clean(fullName));
        batch = clean(batch);
        school = clean(school);
        stream = clean(stream);
        district = clean(district);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String toTitleCase(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean capitalizeNext = true;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.isLetter(codePoint)) {
                result.appendCodePoint(capitalizeNext
                        ? Character.toTitleCase(codePoint)
                        : Character.toLowerCase(codePoint));
                capitalizeNext = false;
            } else {
                result.appendCodePoint(codePoint);
                capitalizeNext = Character.isWhitespace(codePoint)
                        || codePoint == '-'
                        || codePoint == '\''
                        || codePoint == '.';
            }
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }
}
