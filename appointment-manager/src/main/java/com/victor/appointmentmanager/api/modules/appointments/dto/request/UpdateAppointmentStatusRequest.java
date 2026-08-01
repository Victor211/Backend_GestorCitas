package com.victor.appointmentmanager.api.modules.appointments.dto.request;

import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAppointmentStatusRequest {

    @NotNull
    private AppointmentStatus status;

}
