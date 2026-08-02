package com.victor.appointmentmanager.api.modules.employees.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateEmployeeRequest {

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
    @NotEmpty
    @Schema(description = "Ids de los Services del negocio autenticado que el empleado puede realizar. "
            + "Deben pertenecer al Business autenticado y estar activos.", example = "[1]")
    private Set<@NotNull Long> serviceIds;

}
