package com.victor.appointmentmanager.api.modules.appointments.dto.response;

import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {

    private Long id;
    private Long businessId;
    private Long customerId;
    private String customerName;
    private Long employeeId;
    private String employeeName;
    private Long serviceId;
    private String serviceName;
    private Integer serviceDurationMinutes;
    private BigDecimal servicePrice;
    private Instant startAt;
    private Instant endAt;
    private AppointmentStatus status;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;

}
