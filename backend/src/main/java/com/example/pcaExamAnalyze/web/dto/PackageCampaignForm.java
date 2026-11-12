package com.example.pcaExamAnalyze.web.dto;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
@Getter @Setter
public class PackageCampaignForm {
    @NotBlank @Size(max = 160) private String title;
    @NotNull @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") private LocalDateTime endsAt;
    @NotBlank @Pattern(regexp = "[1-9][0-9]{7,14}", message = "Use international digits, e.g. 94779179035") private String whatsappNumber;
}
