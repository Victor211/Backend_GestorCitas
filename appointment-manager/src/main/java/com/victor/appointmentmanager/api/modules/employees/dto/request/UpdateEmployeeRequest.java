package com.victor.appointmentmanager.api.modules.employees.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEmployeeRequest {

    @NotBlank
    @Size(max = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @NotBlank
    @Size(max = 30)
    private String phone;

    @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "^#[A-Fa-f0-9]{6}$", message = "El color debe tener formato hexadecimal, ejemplo: #3B82F6")
    private String color;

    @NotNull
    private Long businessId;

}
