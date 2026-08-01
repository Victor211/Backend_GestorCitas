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
import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import com.victor.appointmentmanager.api.modules.customers.exception.CustomerNotFoundException;
import com.victor.appointmentmanager.api.modules.customers.repository.CustomerRepository;
import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import com.victor.appointmentmanager.api.modules.employees.repository.EmployeeRepository;
import com.victor.appointmentmanager.api.modules.schedule.entity.Schedule;
import com.victor.appointmentmanager.api.modules.schedule.repository.ScheduleRepository;
import com.victor.appointmentmanager.api.modules.services.entity.Service;
import com.victor.appointmentmanager.api.modules.services.repository.ServiceRepository;
import com.victor.appointmentmanager.api.security.BusinessAccessValidator;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final BusinessRepository businessRepository;
    private final CustomerRepository customerRepository;
    private final EmployeeRepository employeeRepository;
    private final ServiceRepository serviceRepository;
    private final ScheduleRepository scheduleRepository;
    private final AppointmentMapper appointmentMapper;
    private final BusinessAccessValidator businessAccessValidator;

    @Override
    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request) {
        businessAccessValidator.validate(request.getBusinessId());
        Business business = findActiveBusinessOrThrow(request.getBusinessId());

        Customer customer = findActiveCustomerOrThrow(request.getCustomerId());
        assertCustomerBelongsToBusiness(customer, business.getId());

        Employee employee = findActiveEmployeeOrThrow(request.getEmployeeId());
        assertEmployeeBelongsToBusiness(employee, business.getId());

        Service service = findActiveServiceOrThrow(request.getServiceId());
        assertServiceBelongsToBusiness(service, business.getId());

        assertEmployeeCanPerformService(employee, service);
        assertNotInPast(request.getStartAt());

        Instant endAt = calculateEndAt(request.getStartAt(), service);

        assertWithinWorkingHours(employee, business, request.getStartAt(), endAt);
        assertNoOverlap(employee.getId(), request.getStartAt(), endAt, null);

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
    public AppointmentResponse findById(Long id, Long businessId) {
        assertBusinessIdProvided(businessId);
        businessAccessValidator.validate(businessId);
        return appointmentMapper.toDto(findByIdAndBusinessOrThrow(id, businessId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> findAll(Long businessId, Long employeeId, Long customerId,
                                              AppointmentStatus status, Instant from, Instant to,
                                              Pageable pageable) {
        assertBusinessIdProvided(businessId);
        businessAccessValidator.validate(businessId);
        assertValidDateRange(from, to);

        return appointmentRepository.search(businessId, employeeId, customerId, status, from, to, pageable)
                .map(appointmentMapper::toDto);
    }

    @Override
    @Transactional
    public AppointmentResponse reschedule(Long id, Long businessId, RescheduleAppointmentRequest request) {
        assertBusinessIdProvided(businessId);
        businessAccessValidator.validate(businessId);
        Appointment appointment = findByIdAndBusinessOrThrow(id, businessId);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessException("No se puede reprogramar una cita cancelada");
        }

        assertNotInPast(request.getStartAt());

        Instant newEndAt = calculateEndAt(request.getStartAt(), appointment.getService());

        assertWithinWorkingHours(appointment.getEmployee(), appointment.getBusiness(), request.getStartAt(), newEndAt);
        assertNoOverlap(appointment.getEmployee().getId(), request.getStartAt(), newEndAt, appointment.getId());

        appointment.setStartAt(request.getStartAt());
        appointment.setEndAt(newEndAt);

        Appointment updated = appointmentRepository.save(appointment);
        return appointmentMapper.toDto(updated);
    }

    @Override
    @Transactional
    public AppointmentResponse updateStatus(Long id, Long businessId, UpdateAppointmentStatusRequest request) {
        assertBusinessIdProvided(businessId);
        businessAccessValidator.validate(businessId);
        Appointment appointment = findByIdAndBusinessOrThrow(id, businessId);

        assertValidStatusTransition(appointment.getStatus(), request.getStatus());
        appointment.setStatus(request.getStatus());

        Appointment updated = appointmentRepository.save(appointment);
        return appointmentMapper.toDto(updated);
    }

    @Override
    @Transactional
    public AppointmentResponse cancel(Long id, Long businessId) {
        assertBusinessIdProvided(businessId);
        businessAccessValidator.validate(businessId);
        Appointment appointment = findByIdAndBusinessOrThrow(id, businessId);

        if (appointment.getStatus() != AppointmentStatus.CANCELLED) {
            appointment.setStatus(AppointmentStatus.CANCELLED);
            appointment = appointmentRepository.save(appointment);
        }

        return appointmentMapper.toDto(appointment);
    }

    private Appointment findByIdAndBusinessOrThrow(Long id, Long businessId) {
        return appointmentRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new AppointmentNotFoundException("Cita no encontrada con id " + id));
    }

    private Business findActiveBusinessOrThrow(Long businessId) {
        return businessRepository.findByIdAndActiveTrue(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con id " + businessId));
    }

    private Customer findActiveCustomerOrThrow(Long customerId) {
        return customerRepository.findByIdAndActiveTrue(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Cliente no encontrado con id " + customerId));
    }

    private Employee findActiveEmployeeOrThrow(Long employeeId) {
        return employeeRepository.findByIdAndActiveTrue(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con id " + employeeId));
    }

    private Service findActiveServiceOrThrow(Long serviceId) {
        return serviceRepository.findByIdAndActiveTrue(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con id " + serviceId));
    }

    private void assertCustomerBelongsToBusiness(Customer customer, Long businessId) {
        if (!customer.getBusiness().getId().equals(businessId)) {
            throw new BusinessException("El cliente pertenece a otro negocio");
        }
    }

    private void assertEmployeeBelongsToBusiness(Employee employee, Long businessId) {
        if (!employee.getBusiness().getId().equals(businessId)) {
            throw new BusinessException("El empleado pertenece a otro negocio");
        }
    }

    private void assertServiceBelongsToBusiness(Service service, Long businessId) {
        if (!service.getBusiness().getId().equals(businessId)) {
            throw new BusinessException("El servicio pertenece a otro negocio");
        }
    }

    private void assertEmployeeCanPerformService(Employee employee, Service service) {
        boolean canPerform = employee.getServices().stream()
                .anyMatch(assignedService -> assignedService.getId().equals(service.getId()));
        if (!canPerform) {
            throw new BusinessException("El empleado no está habilitado para realizar este servicio");
        }
    }

    private void assertNotInPast(Instant startAt) {
        if (startAt.isBefore(Instant.now())) {
            throw new BusinessException("La fecha de la cita no puede estar en el pasado");
        }
    }

    private Instant calculateEndAt(Instant startAt, Service service) {
        return startAt.plus(service.getDurationMinutes(), ChronoUnit.MINUTES);
    }

    private void assertWithinWorkingHours(Employee employee, Business business, Instant startAt, Instant endAt) {
        ZoneId zoneId = resolveZoneId(business);

        ZonedDateTime localStart = startAt.atZone(zoneId);
        ZonedDateTime localEnd = endAt.atZone(zoneId);

        DayOfWeek dayOfWeek = localStart.getDayOfWeek();
        LocalTime localStartTime = localStart.toLocalTime();
        LocalTime localEndTime = localEnd.toLocalTime();

        List<Schedule> daySchedules = scheduleRepository
                .findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(employee.getId(), dayOfWeek);

        boolean fitsWithinSingleSchedule = daySchedules.stream()
                .anyMatch(schedule -> !localStartTime.isBefore(schedule.getStartTime())
                        && !localEndTime.isAfter(schedule.getEndTime()));

        if (!fitsWithinSingleSchedule) {
            throw new BusinessException("La cita está fuera del horario laboral del empleado");
        }
    }

    private ZoneId resolveZoneId(Business business) {
        try {
            return ZoneId.of(business.getTimezone());
        } catch (DateTimeException ex) {
            throw new BusinessException("La zona horaria configurada en el negocio no es válida");
        }
    }

    private void assertNoOverlap(Long employeeId, Instant startAt, Instant endAt, Long excludeId) {
        boolean overlaps = appointmentRepository.existsOverlapping(
                employeeId, startAt, endAt, excludeId, AppointmentStatus.CANCELLED);
        if (overlaps) {
            throw new BusinessException("La cita se superpone con otra cita existente del empleado");
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
            throw new BusinessException("El rango de fechas no es válido: 'from' debe ser anterior o igual a 'to'");
        }
    }

    private void assertBusinessIdProvided(Long businessId) {
        if (businessId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "businessId es obligatorio");
        }
    }

}
