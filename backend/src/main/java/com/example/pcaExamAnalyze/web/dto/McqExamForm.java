package com.example.pcaExamAnalyze.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.example.pcaExamAnalyze.domain.McqSheetType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class McqExamForm {

    @NotBlank(message = "Exam name is required")
    @Size(max = 180, message = "Exam name is too long")
    private String name;

    @NotBlank(message = "Select a batch")
    private String batch;

    @NotNull(message = "Number of questions is required")
    @Min(value = 0, message = "Question count cannot be negative")
    @Max(value = 50, message = "Question count cannot exceed 50")
    private Integer totalQuestions;

    @NotNull(message = "Exam year is required")
    @Min(value = 2024, message = "Enter a valid exam year")
    @Max(value = 2100, message = "Enter a valid exam year")
    private Integer examYear;

    @NotBlank(message = "Exam month is required")
    private String examMonth;

    /** Required only for the classic answer sheet; checked in McqAdminService.validateForm. */
    @Pattern(regexp = "^$|^https://(?:drive\\.google\\.com|docs\\.google\\.com)/.+$",
            message = "Enter a valid Google Drive link")
    @Size(max = 1000, message = "PDF link is too long")
    private String paperDriveUrl;

    @NotNull(message = "Opening date and time are required")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime openAt;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime closeAt;

    @NotNull(message = "Result release date and time are required")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime resultReleaseAt;

    @Size(max = 3000, message = "Instructions are too long")
    private String instructions;

    @Min(value = 1, message = "Duration must be positive")
    @Max(value = 600, message = "Duration is too long")
    private Integer durationMinutes;

    private boolean allowResubmission;

    @NotNull(message = "Select a sheet type")
    private McqSheetType sheetType = McqSheetType.ANSWER_SHEET;

    private Map<Integer, List<Integer>> answers = new LinkedHashMap<>();
}
