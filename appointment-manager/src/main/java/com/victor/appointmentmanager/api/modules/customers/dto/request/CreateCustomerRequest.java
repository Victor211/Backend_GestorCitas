package com.victor.appointmentmanager.api.modules.customers.dto.request;

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
public class CreateCustomerRequest {

    @NotBlank
    @Size(min = 2, max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @NotBlank
    @Size(max = 30)
    private String phone;

    @Email
    @Size(max = 150)
    private String email;

    @Size(max = 500)
    private String notes;

}
