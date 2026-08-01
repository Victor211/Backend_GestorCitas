package com.victor.appointmentmanager.api.modules.customers.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String notes;
    private Long businessId;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;

}
