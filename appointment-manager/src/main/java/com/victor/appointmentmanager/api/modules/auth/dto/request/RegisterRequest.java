package com.victor.appointmentmanager.api.modules.auth.dto.request;

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
public class RegisterRequest {

    @NotBlank
    @Size(min = 2, max = 100)
    private String ownerFirstName;

    @NotBlank
    @Size(min = 2, max = 100)
    private String ownerLastName;

    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    @NotBlank
    @Size(min = 8)
    private String password;

    @NotBlank
    @Size(max = 150)
    private String businessName;

    @NotBlank
    @Size(max = 30)
    private String businessPhone;

    @Email
    private String businessEmail;

    private String businessAddress;

    @NotBlank
    private String businessTimezone;

}
