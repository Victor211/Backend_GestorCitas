package com.victor.appointmentmanager.api.modules.employees.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String color;
    private Long businessId;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;

}
