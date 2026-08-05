package com.victor.appointmentmanager.api.modules.employees.dto.request;

import com.victor.appointmentmanager.api.common.util.StringNormalizer;
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

    @Size(max = 30)
    @Schema(description = "Teléfono del empleado (opcional).", nullable = true)
    private String phone;

    @Email
    @Size(max = 150)
    @Schema(description = "Email del empleado (opcional).", nullable = true)
    private String email;

    @NotBlank
    @Pattern(regexp = "^#[A-Fa-f0-9]{6}$", message = "El color debe tener formato hexadecimal, ejemplo: #3B82F6")
    private String color;

    @NotNull
    @NotEmpty
    @Schema(description = "Ids de los Services del negocio autenticado que el empleado puede realizar. "
            + "Deben pertenecer al Business autenticado y estar activos.", example = "[1]")
    private Set<@NotNull Long> serviceIds;

    public void setPhone(String phone) {
        this.phone = StringNormalizer.blankToNull(phone);
    }

    public void setEmail(String email) {
        this.email = StringNormalizer.blankToNull(email);
    }

}
