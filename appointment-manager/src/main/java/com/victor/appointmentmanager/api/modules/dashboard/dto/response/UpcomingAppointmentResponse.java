package com.victor.appointmentmanager.api.modules.dashboard.dto.response;

import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingAppointmentResponse {

    private Long id;
    private Long customerId;
    private String customerName;
    private Long employeeId;
    private String employeeName;
    private Long serviceId;
    private String serviceName;
    private Instant startAt;
    private Instant endAt;
    private AppointmentStatus status;

}
