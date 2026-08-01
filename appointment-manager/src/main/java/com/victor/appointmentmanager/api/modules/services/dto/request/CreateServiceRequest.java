package com.victor.appointmentmanager.api.modules.services.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateServiceRequest {

    @NotBlank
    @Size(min = 3, max = 100)
    private String name;

    @Size(max = 255)
    private String description;

    @NotNull
    @Min(1)
    @Max(480)
    private Integer durationMinutes;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal price;

    @NotBlank
    @Pattern(regexp = "^#[A-Fa-f0-9]{6}$", message = "El color debe tener formato hexadecimal, ejemplo: #3B82F6")
    private String color;

    @NotNull
    private Long businessId;

}
