package com.victor.appointmentmanager.api.modules.employees.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeServiceResponse {

    private Long id;
    private String name;
    private Integer durationMinutes;
    private BigDecimal price;
    private String color;

}
