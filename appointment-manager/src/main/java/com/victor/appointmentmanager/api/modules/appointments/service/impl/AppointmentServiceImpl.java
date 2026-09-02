package com.victor.appointmentmanager.api.modules.appointments.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.CreateAppointmentRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.RescheduleAppointmentRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.UpdateAppointmentStatusRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.response.AppointmentResponse;
import com.victor.appointmentmanager.api.modules.appointments.entity.Appointment;
import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
import com.victor.appointmentmanager.api.modules.appointments.exception.AppointmentNotFoundException;
import com.victor.appointmentmanager.api.modules.appointments.mapper.AppointmentMapper;
import com.victor.appointmentmanager.api.modules.appointments.repository.AppointmentRepository;
import com.victor.appointmentmanager.api.modules.appointments.service.AppointmentService;
import com.victor.appointmentmanager.api.modules.appointments.service.AvailabilityCheck;
import com.victor.appointmentmanager.api.modules.appointments.service.AvailabilityReason;
import com.victor.appointmentmanager.api.modules.appointments.specification.AppointmentSpecifications;
import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import com.victor.appointmentmanager.api.modules.customers.exception.CustomerNotFoundException;
import com.victor.appointmentmanager.api.modules.customers.repository.CustomerRepository;
import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import com.victor.appointmentmanager.api.modules.employees.repository.EmployeeRepository;
import com.victor.appointmentmanager.api.modules.schedule.entity.Schedule;
import com.victor.appointmentmanager.api.modules.schedule.repository.ScheduleRepository;
import com.victor.appointmentmanager.api.modules.services.entity.Service;
import com.victor.appointmentmanager.api.modules.services.repository.ServiceRepository;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    /**
     * Duración asumida para una consulta de disponibilidad que todavía no tiene un Service
     * resuelto (ver {@link #isAvailable(Long, Long, Long, Instant)}). Nunca se usa para crear una
     * cita real: {@link #create(Long, CreateAppointmentRequest)} siempre exige un Service válido.
     */
    private static final int DEFAULT_AVAILABILITY_CHECK_DURATION_MINUTES = 30;

    /**
     * Paso entre candidatos consecutivos al generar horarios reales en {@link #findAvailableSlots}.
     * Cada candidato igual se valida contra la duración real del Service y contra citas existentes
     * ({@link #evaluateBookability}); este paso solo define la granularidad de los horarios que se
     * ofrecen (ej. 10:00, 10:30, 11:00...), pensado para una lista breve en WhatsApp, no un
     * calendario completo minuto a minuto.
     */
    private static final int SLOT_STEP_MINUTES = 30;

    private final AppointmentRepository appointmentRepository;
    private final BusinessRepository businessRepository;
    private final CustomerRepository customerRepository;
    private final EmployeeRepository employeeRepository;
    private final ServiceRepository serviceRepository;
    private final ScheduleRepository scheduleRepository;
    private final AppointmentMapper appointmentMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request) {
        return create(currentUserProvider.getCurrentBusinessId(), request);
    }

    @Override
    @Transactional
    public AppointmentResponse create(Long businessId, CreateAppointmentRequest request) {
        Business business = findActiveBusinessOrThrow(businessId);

        Customer customer = findOwnedCustomerOrThrow(request.getCustomerId(), businessId);
        Employee employee = findOwnedEmployeeOrThrow(request.getEmployeeId(), businessId);
        Service service = findOwnedServiceOrThrow(request.getServiceId(), businessId);

        Instant endAt = calculateEndAt(request.getStartAt(), service);
        assertBookable(business, employee, service, request.getStartAt(), endAt, null);

        Appointment appointment = appointmentMapper.toEntity(request);
        appointment.setBusiness(business);
        appointment.setCustomer(customer);
        appointment.setEmployee(employee);
        appointment.setService(service);
        appointment.setEndAt(endAt);
        appointment.setStatus(AppointmentStatus.CONFIRMED);

        Appointment saved = appointmentRepository.save(appointment);
        return appointmentMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse findById(Long id) {
        return appointmentMapper.toDto(findByIdAndBusinessOrThrow(id, currentUserProvider.getCurrentBusinessId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> findAll(Long employeeId, Long customerId,
                                              AppointmentStatus status, Instant from, Instant to,
                                              Pageable pageable) {
        assertValidDateRange(from, to);

        Specification<Appointment> specification = AppointmentSpecifications.filterBy(
                currentUserProvider.getCurrentBusinessId(), employeeId, customerId, status, from, to);

        return appointmentRepository.findAll(specification, withDefaultSort(pageable))
                .map(appointmentMapper::toDto);
    }

    @Override
    @Transactional
    public AppointmentResponse reschedule(Long id, RescheduleAppointmentRequest request) {
        return reschedule(currentUserProvider.getCurrentBusinessId(), id, request);
    }

    @Override
    @Transactional
    public AppointmentResponse reschedule(Long businessId, Long id, RescheduleAppointmentRequest request) {
        Appointment appointment = findByIdAndBusinessOrThrow(id, businessId);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessException("No se puede reprogramar una cita cancelada");
        }

        Instant newEndAt = calculateEndAt(request.getStartAt(), appointment.getService());
        assertBookable(appointment.getBusiness(), appointment.getEmployee(), appointment.getService(),
                request.getStartAt(), newEndAt, appointment.getId());

        appointment.setStartAt(request.getStartAt());
        appointment.setEndAt(newEndAt);

        Appointment updated = appointmentRepository.save(appointment);
        return appointmentMapper.toDto(updated);
    }

    @Override
    @Transactional
    public AppointmentResponse updateStatus(Long id, UpdateAppointmentStatusRequest request) {
        Appointment appointment = findByIdAndBusinessOrThrow(id, currentUserProvider.getCurrentBusinessId());

        assertValidStatusTransition(appointment.getStatus(), request.getStatus());
        appointment.setStatus(request.getStatus());

        Appointment updated = appointmentRepository.save(appointment);
        return appointmentMapper.toDto(updated);
    }

    @Override
    @Transactional
    public AppointmentResponse cancel(Long id) {
        return cancel(currentUserProvider.getCurrentBusinessId(), id);
    }

    @Override
    @Transactional
    public AppointmentResponse cancel(Long businessId, Long id) {
        Appointment appointment = findByIdAndBusinessOrThrow(id, businessId);

        if (appointment.getStatus() != AppointmentStatus.CANCELLED) {
            appointment.setStatus(AppointmentStatus.CANCELLED);
            appointment = appointmentRepository.save(appointment);
        }

        return appointmentMapper.toDto(appointment);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAvailable(Long businessId, Long employeeId, Long serviceId, Instant startAt) {
        return checkAvailability(businessId, employeeId, serviceId, startAt).available();
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityCheck checkAvailability(Long businessId, Long employeeId, Long serviceId, Instant startAt) {
        Business business = findActiveBusinessOrThrow(businessId);
        Optional<Employee> employee = employeeRepository.findByIdAndBusinessIdAndActiveTrue(employeeId, businessId);
        if (employee.isEmpty()) {
            return AvailabilityCheck.unavailable(AvailabilityReason.EMPLOYEE_NOT_FOUND);
        }

        Service service = null;
        Instant endAt;
        if (serviceId != null) {
            Optional<Service> resolvedService = serviceRepository.findByIdAndBusinessIdAndActiveTrue(serviceId, businessId);
            if (resolvedService.isEmpty()) {
                return AvailabilityCheck.unavailable(AvailabilityReason.SERVICE_NOT_FOUND);
            }
            service = resolvedService.get();
            endAt = calculateEndAt(startAt, service);
        } else {
            endAt = startAt.plus(DEFAULT_AVAILABILITY_CHECK_DURATION_MINUTES, ChronoUnit.MINUTES);
        }

        AvailabilityReason reason = evaluateBookability(business, employee.get(), service, startAt, endAt, null);
        return reason == null ? AvailabilityCheck.AVAILABLE : AvailabilityCheck.unavailable(reason);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Instant> findAvailableSlots(Long businessId, Long employeeId, Long serviceId, LocalDate date,
                                             int maxSlots) {
        if (maxSlots <= 0 || date == null) {
            return List.of();
        }
        Business business = findActiveBusinessOrThrow(businessId);
        Optional<Employee> employeeOpt = employeeRepository.findByIdAndBusinessIdAndActiveTrue(employeeId, businessId);
        Optional<Service> serviceOpt = serviceRepository.findByIdAndBusinessIdAndActiveTrue(serviceId, businessId);
        if (employeeOpt.isEmpty() || serviceOpt.isEmpty()) {
            return List.of();
        }
        Employee employee = employeeOpt.get();
        Service service = serviceOpt.get();
        if (!canPerformService(employee, service)) {
            return List.of();
        }

        ZoneId zoneId = resolveZoneId(business);
        List<Schedule> daySchedules = scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                employeeId, date.getDayOfWeek());

        List<Instant> slots = new ArrayList<>();
        for (Schedule daySchedule : daySchedules) {
            int startMinuteOfDay = daySchedule.getStartTime().toSecondOfDay() / 60;
            int endMinuteOfDay = daySchedule.getEndTime().toSecondOfDay() / 60;
            for (int minuteOfDay = startMinuteOfDay; minuteOfDay + service.getDurationMinutes() <= endMinuteOfDay;
                    minuteOfDay += SLOT_STEP_MINUTES) {
                LocalTime candidateTime = LocalTime.ofSecondOfDay(minuteOfDay * 60L);
                Instant candidateStart = ZonedDateTime.of(date, candidateTime, zoneId).toInstant();
                Instant candidateEnd = candidateStart.plus(service.getDurationMinutes(), ChronoUnit.MINUTES);
                if (evaluateBookability(business, employee, service, candidateStart, candidateEnd, null) == null) {
                    slots.add(candidateStart);
                    if (slots.size() >= maxSlots) {
                        return slots;
                    }
                }
            }
        }
        return slots;
    }

    /**
     * Única implementación de "¿esta cita puede reservarse?": la usan tanto {@link
     * #create(Long, CreateAppointmentRequest)}/{@link #reschedule(Long, Long, RescheduleAppointmentRequest)}
     * (revalidación dura, antes de persistir) como {@link #checkAvailability} / {@link
     * #findAvailableSlots} (consultas blandas, sin persistir). {@code service} es {@code null} solo
     * cuando la llama {@code checkAvailability} sin un Service resuelto: en ese caso se omite la
     * verificación empleado-servicio.
     */
    private void assertBookable(Business business, Employee employee, Service service, Instant startAt,
                                 Instant endAt, Long excludeAppointmentId) {
        AvailabilityReason reason = evaluateBookability(business, employee, service, startAt, endAt, excludeAppointmentId);
        if (reason != null) {
            throw new BusinessException(messageForReason(reason));
        }
    }

    /**
     * Evalúa, en el mismo orden que antes lanzaba excepciones {@code assertBookable}, cuál es el
     * primer motivo por el que un horario no puede reservarse ({@code null} si puede). Única fuente
     * de verdad de las reglas de disponibilidad: tanto la revalidación dura (que la envuelve en una
     * excepción) como las consultas blandas ({@code checkAvailability}, {@code findAvailableSlots})
     * pasan por acá.
     */
    private AvailabilityReason evaluateBookability(Business business, Employee employee, Service service,
                                                     Instant startAt, Instant endAt, Long excludeAppointmentId) {
        if (service != null && !canPerformService(employee, service)) {
            return AvailabilityReason.EMPLOYEE_CANNOT_PERFORM_SERVICE;
        }
        if (startAt.isBefore(Instant.now())) {
            return AvailabilityReason.IN_PAST;
        }
        if (!fitsWithinWorkingHours(employee, business, startAt, endAt)) {
            return AvailabilityReason.OUTSIDE_SCHEDULE;
        }
        if (appointmentRepository.existsOverlapping(employee.getId(), startAt, endAt, excludeAppointmentId,
                AppointmentStatus.CANCELLED)) {
            return AvailabilityReason.OVERLAPPING;
        }
        return null;
    }

    private String messageForReason(AvailabilityReason reason) {
        return switch (reason) {
            case EMPLOYEE_CANNOT_PERFORM_SERVICE -> "El empleado no está habilitado para realizar este servicio";
            case IN_PAST -> "La fecha de la cita no puede estar en el pasado";
            case OUTSIDE_SCHEDULE -> "La cita está fuera del horario laboral del empleado";
            case OVERLAPPING -> "La cita se superpone con otra cita existente del empleado";
            case EMPLOYEE_NOT_FOUND, SERVICE_NOT_FOUND -> "No se pudo validar la disponibilidad solicitada";
        };
    }

    private Appointment findByIdAndBusinessOrThrow(Long id, Long businessId) {
        return appointmentRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new AppointmentNotFoundException("Cita no encontrada con id " + id));
    }

    private Business findActiveBusinessOrThrow(Long businessId) {
        return businessRepository.findByIdAndActiveTrue(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con id " + businessId));
    }

    private Customer findOwnedCustomerOrThrow(Long customerId, Long businessId) {
        return customerRepository.findByIdAndBusinessIdAndActiveTrue(customerId, businessId)
                .orElseThrow(() -> new CustomerNotFoundException("Cliente no encontrado con id " + customerId));
    }

    private Employee findOwnedEmployeeOrThrow(Long employeeId, Long businessId) {
        return employeeRepository.findByIdAndBusinessIdAndActiveTrue(employeeId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con id " + employeeId));
    }

    private Service findOwnedServiceOrThrow(Long serviceId, Long businessId) {
        return serviceRepository.findByIdAndBusinessIdAndActiveTrue(serviceId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con id " + serviceId));
    }

    private boolean canPerformService(Employee employee, Service service) {
        return employee.getServices().stream()
                .anyMatch(assignedService -> assignedService.getId().equals(service.getId()));
    }

    private Instant calculateEndAt(Instant startAt, Service service) {
        return startAt.plus(service.getDurationMinutes(), ChronoUnit.MINUTES);
    }

    private boolean fitsWithinWorkingHours(Employee employee, Business business, Instant startAt, Instant endAt) {
        ZoneId zoneId = resolveZoneId(business);

        ZonedDateTime localStart = startAt.atZone(zoneId);
        ZonedDateTime localEnd = endAt.atZone(zoneId);

        DayOfWeek dayOfWeek = localStart.getDayOfWeek();
        LocalTime localStartTime = localStart.toLocalTime();
        LocalTime localEndTime = localEnd.toLocalTime();

        List<Schedule> daySchedules = scheduleRepository
                .findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(employee.getId(), dayOfWeek);

        return daySchedules.stream()
                .anyMatch(schedule -> !localStartTime.isBefore(schedule.getStartTime())
                        && !localEndTime.isAfter(schedule.getEndTime()));
    }

    private ZoneId resolveZoneId(Business business) {
        try {
            return ZoneId.of(business.getTimezone());
        } catch (DateTimeException ex) {
            throw new BusinessException("La zona horaria configurada en el negocio no es válida");
        }
    }

    private void assertValidStatusTransition(AppointmentStatus currentStatus, AppointmentStatus newStatus) {
        if (newStatus == AppointmentStatus.PENDING) {
            throw new BusinessException("No se puede establecer manualmente el estado PENDING");
        }
        if (currentStatus == AppointmentStatus.CANCELLED) {
            throw new BusinessException("No se puede cambiar el estado de una cita cancelada");
        }
    }

    private void assertValidDateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El rango de fechas no es válido: 'from' debe ser anterior o igual a 'to'");
        }
    }

    private Pageable withDefaultSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.ASC, "startAt"));
        }
        return pageable;
    }

}
