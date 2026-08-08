package com.victor.appointmentmanager.api.modules.settings.dto.request;

import com.victor.appointmentmanager.api.common.util.StringNormalizer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBusinessSettingsRequest {

    @NotBlank
    @Size(min = 2, max = 150)
    private String name;

    @Size(max = 30)
    @Schema(description = "Teléfono de contacto del negocio (opcional).", nullable = true)
    private String phone;

    @Email
    @Size(max = 150)
    @Schema(description = "Email de contacto del negocio (opcional).", nullable = true)
    private String email;

    @Size(max = 255)
    @Schema(description = "Dirección del negocio (opcional).", nullable = true)
    private String address;

    @NotBlank
    @Schema(description = "Zona horaria IANA del negocio, debe ser un ZoneId válido.", example = "America/Asuncion")
    private String timezone;

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public void setPhone(String phone) {
        this.phone = StringNormalizer.blankToNull(phone);
    }

    public void setEmail(String email) {
        this.email = StringNormalizer.blankToNull(email);
    }

    public void setAddress(String address) {
        this.address = StringNormalizer.blankToNull(address);
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone == null ? null : timezone.trim();
    }

}
