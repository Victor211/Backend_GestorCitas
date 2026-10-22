package com.victor.appointmentmanager.api.modules.users.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.victor.appointmentmanager.api.modules.users.enums.UserRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.charset.StandardCharsets;

/**
 * No contiene businessId: el negocio del nuevo usuario siempre se toma del OWNER autenticado.
 * Sin @ToString a propósito, para que la contraseña nunca termine en un log.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    private static final int BCRYPT_MAX_BYTES = 72;

    @NotBlank
    @Size(min = 2, max = 100)
    private String firstName;

    @NotBlank
    @Size(min = 2, max = 100)
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotNull
    private UserRole role;

    // BCrypt solo considera los primeros 72 bytes: se rechaza en vez de truncar en silencio.
    @JsonIgnore
    @AssertTrue(message = "La contraseña no puede superar los 72 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }

}
