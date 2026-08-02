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

    /**
     * Used only by internal, non-JWT channels (e.g. the WhatsApp conversational flow) that already
     * resolved the Business by other means (phone_number_id) and cannot rely on the SecurityContext.
     */
    AppointmentResponse create(Long businessId, CreateAppointmentRequest request);

    AppointmentResponse findById(Long id);

    Page<AppointmentResponse> findAll(Long employeeId, Long customerId,
                                       AppointmentStatus status, Instant from, Instant to, Pageable pageable);

    AppointmentResponse reschedule(Long id, RescheduleAppointmentRequest request);

    AppointmentResponse reschedule(Long businessId, Long id, RescheduleAppointmentRequest request);

    AppointmentResponse updateStatus(Long id, UpdateAppointmentStatusRequest request);

    AppointmentResponse cancel(Long id);

    AppointmentResponse cancel(Long businessId, Long id);

}
