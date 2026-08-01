package com.victor.appointmentmanager.api.modules.appointments.service;

import com.victor.appointmentmanager.api.modules.appointments.dto.request.CreateAppointmentRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.RescheduleAppointmentRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.UpdateAppointmentStatusRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.response.AppointmentResponse;
import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface AppointmentService {

    AppointmentResponse create(CreateAppointmentRequest request);

    AppointmentResponse findById(Long id, Long businessId);

    Page<AppointmentResponse> findAll(Long businessId, Long employeeId, Long customerId,
                                       AppointmentStatus status, Instant from, Instant to, Pageable pageable);

    AppointmentResponse reschedule(Long id, Long businessId, RescheduleAppointmentRequest request);

    AppointmentResponse updateStatus(Long id, Long businessId, UpdateAppointmentStatusRequest request);

    AppointmentResponse cancel(Long id, Long businessId);

}
