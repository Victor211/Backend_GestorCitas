package com.victor.appointmentmanager.api.modules.appointments.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateAppointmentRequest {

    @NotNull
    private Long businessId;

    @NotNull
    private Long customerId;

    @NotNull
    private Long employeeId;

    @NotNull
    private Long serviceId;

    @NotNull
    private Instant startAt;

    @Size(max = 500)
    private String notes;

}
