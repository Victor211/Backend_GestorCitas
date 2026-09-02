package com.victor.appointmentmanager.api.modules.appointments.service;

import com.victor.appointmentmanager.api.modules.appointments.dto.request.CreateAppointmentRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.RescheduleAppointmentRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.UpdateAppointmentStatusRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.response.AppointmentResponse;
import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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

    /**
     * Única fuente de verdad para "¿este horario está disponible?": aplica exactamente las mismas
     * reglas (empleado-servicio, no en el pasado, horario laboral, sin solapamiento) que {@link
     * #create(Long, CreateAppointmentRequest)} usa para revalidar antes de persistir. Pensado para
     * consultas previas (p. ej. el flujo conversacional de WhatsApp) que necesitan comprobar
     * disponibilidad sin llegar a crear la cita. {@code serviceId} es opcional: si es {@code null}
     * se evalúa con una duración por defecto y sin verificar la asignación empleado-servicio, para
     * cubrir consultas de disponibilidad genéricas ("¿Juan está libre a las 16?") donde el cliente
     * todavía no mencionó un servicio. Nunca lanza excepción por indisponibilidad: devuelve {@code
     * false}, incluyendo cuando el negocio/empleado/servicio no existen.
     */
    boolean isAvailable(Long businessId, Long employeeId, Long serviceId, Instant startAt);

    /**
     * Misma evaluación que {@link #isAvailable}, pero exponiendo el motivo exacto por el cual un
     * horario no puede reservarse ({@link AvailabilityReason}) en vez de solo un booleano. Pensado
     * para redactar mensajes específicos ("ocupado" vs "fuera de horario" vs "no realiza ese
     * servicio") en el flujo conversacional, sin duplicar las reglas de negocio.
     */
    AvailabilityCheck checkAvailability(Long businessId, Long employeeId, Long serviceId, Instant startAt);

    /**
     * Horarios de inicio realmente libres para un empleado, servicio y fecha concretos: aplica
     * exactamente las mismas reglas que {@link #checkAvailability} (horario laboral, duración real
     * del servicio, sin solapamiento con citas existentes), evaluando cada candidato con ellas en
     * vez de derivarlos del Schedule bruto. Los horarios pasados no se incluyen. Devuelve como
     * máximo {@code maxSlots} horarios, en orden cronológico. Lista vacía si el empleado/servicio no
     * existen, el empleado no realiza el servicio, o no hay horarios libres ese día.
     */
    List<Instant> findAvailableSlots(Long businessId, Long employeeId, Long serviceId, LocalDate date, int maxSlots);

}
