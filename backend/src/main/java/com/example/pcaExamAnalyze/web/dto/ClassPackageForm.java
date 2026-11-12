package com.example.pcaExamAnalyze.web.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class ClassPackageForm {
    @NotBlank @Size(max = 160) private String name;
    @NotBlank @Pattern(regexp = "Theory|Self Study|Practical|Other") private String category = "Theory";
    @Size(max = 100) private String batch;
    @Size(max = 2000) private String description;
    @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) private BigDecimal originalPrice;
    @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) private BigDecimal offerPrice;
    @Min(1) @Max(100000) private int capacity = 111;
    @Min(0) @Max(100000) private Integer displayOrder;
    private boolean published;
}
