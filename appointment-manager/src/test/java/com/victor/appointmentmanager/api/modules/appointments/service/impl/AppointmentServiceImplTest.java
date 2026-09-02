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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Asuncion");

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private AppointmentMapper appointmentMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private AppointmentServiceImpl appointmentService;

    private Business business;
    private Customer customer;
    private Employee employee;
    private Service service;
    private Schedule schedule;
    private Instant startAt;
    private Instant endAt;

    @BeforeEach
    void setUp() {
        business = new Business();
        business.setId(1L);
        business.setName("Barbería Central");
        business.setTimezone("America/Asuncion");

        customer = new Customer();
        customer.setId(2L);
        customer.setFirstName("Ana");
        customer.setLastName("Gómez");
        customer.setBusiness(business);

        service = new Service();
        service.setId(4L);
        service.setName("Corte");
        service.setDurationMinutes(30);
        service.setPrice(new BigDecimal("15.00"));
        service.setBusiness(business);

        employee = new Employee();
        employee.setId(3L);
        employee.setFirstName("Juan");
        employee.setLastName("Pérez");
        employee.setBusiness(business);
        employee.setServices(Set.of(service));

        LocalDate futureMonday = LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        startAt = ZonedDateTime.of(futureMonday, LocalTime.of(10, 0), ZONE).toInstant();
        endAt = startAt.plusSeconds(30 * 60);

        schedule = new Schedule();
        schedule.setId(50L);
        schedule.setEmployee(employee);
        schedule.setDayOfWeek(DayOfWeek.MONDAY);
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(12, 0));

        lenient().when(currentUserProvider.getCurrentBusinessId()).thenReturn(1L);
    }

    private CreateAppointmentRequest buildCreateRequest() {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setCustomerId(2L);
        request.setEmployeeId(3L);
        request.setServiceId(4L);
        request.setStartAt(startAt);
        return request;
    }

    @Test
    void createsAppointmentSuccessfully() {
        CreateAppointmentRequest request = buildCreateRequest();
        Appointment appointment = new Appointment();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(2L, 1L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(3L, startAt, endAt, null, AppointmentStatus.CANCELLED))
                .thenReturn(false);
        when(appointmentMapper.toEntity(request)).thenReturn(appointment);
        when(appointmentRepository.save(appointment)).thenReturn(appointment);

        AppointmentResponse response = new AppointmentResponse();
        response.setId(100L);
        when(appointmentMapper.toDto(appointment)).thenReturn(response);

        AppointmentResponse result = appointmentService.create(request);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(appointment.getBusiness()).isEqualTo(business);
        assertThat(appointment.getCustomer()).isEqualTo(customer);
        assertThat(appointment.getEmployee()).isEqualTo(employee);
        assertThat(appointment.getService()).isEqualTo(service);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(appointmentRepository).save(appointment);
    }

    @Test
    void calculatesEndAtFromServiceDuration() {
        CreateAppointmentRequest request = buildCreateRequest();
        Appointment appointment = new Appointment();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(2L, 1L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(3L, startAt, endAt, null, AppointmentStatus.CANCELLED))
                .thenReturn(false);
        when(appointmentMapper.toEntity(request)).thenReturn(appointment);
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        appointmentService.create(request);

        assertThat(appointment.getEndAt()).isEqualTo(startAt.plusSeconds(1800));
    }

    @Test
    void throwsWhenStartAtIsInThePast() {
        CreateAppointmentRequest request = buildCreateRequest();
        request.setStartAt(Instant.now().minusSeconds(3600));

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(2L, 1L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void throwsNotFoundWhenCustomerBelongsToAnotherBusiness() {
        CreateAppointmentRequest request = buildCreateRequest();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(2L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(CustomerNotFoundException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void throwsNotFoundWhenEmployeeBelongsToAnotherBusiness() {
        CreateAppointmentRequest request = buildCreateRequest();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(2L, 1L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void throwsNotFoundWhenServiceBelongsToAnotherBusiness() {
        CreateAppointmentRequest request = buildCreateRequest();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(2L, 1L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void throwsWhenEmployeeIsNotEnabledForService() {
        employee.setServices(Set.of());

        CreateAppointmentRequest request = buildCreateRequest();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(2L, 1L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void throwsWhenAppointmentStartsBeforeScheduleWindow() {
        Instant earlyStart = ZonedDateTime.of(
                LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY)),
                LocalTime.of(7, 45), ZONE).toInstant();

        CreateAppointmentRequest request = buildCreateRequest();
        request.setStartAt(earlyStart);

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(2L, 1L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void throwsWhenAppointmentEndsAfterScheduleWindow() {
        Instant lateStart = ZonedDateTime.of(
                LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY)),
                LocalTime.of(11, 45), ZONE).toInstant();

        CreateAppointmentRequest request = buildCreateRequest();
        request.setStartAt(lateStart);

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(2L, 1L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void throwsWhenAppointmentOverlapsWithAnotherOne() {
        CreateAppointmentRequest request = buildCreateRequest();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(2L, 1L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(3L, startAt, endAt, null, AppointmentStatus.CANCELLED))
                .thenReturn(true);

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void allowsContiguousAppointments() {
        CreateAppointmentRequest request = buildCreateRequest();
        Appointment appointment = new Appointment();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(2L, 1L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(3L, startAt, endAt, null, AppointmentStatus.CANCELLED))
                .thenReturn(false);
        when(appointmentMapper.toEntity(request)).thenReturn(appointment);
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        appointmentService.create(request);

        verify(appointmentRepository).save(appointment);
    }

    @Test
    void throwsWhenBusinessTimezoneIsInvalid() {
        business.setTimezone("Not/AValidZone");

        CreateAppointmentRequest request = buildCreateRequest();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(2L, 1L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void createUsingExplicitBusinessIdOverloadUsedByAiChannel() {
        CreateAppointmentRequest request = buildCreateRequest();
        Appointment appointment = new Appointment();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(2L, 1L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(3L, startAt, endAt, null, AppointmentStatus.CANCELLED))
                .thenReturn(false);
        when(appointmentMapper.toEntity(request)).thenReturn(appointment);
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        appointmentService.create(1L, request);

        verify(appointmentRepository).save(appointment);
    }

    @Test
    void reschedulesAppointmentSuccessfully() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);

        RescheduleAppointmentRequest request = new RescheduleAppointmentRequest();
        Instant newStart = startAt.plusSeconds(3600);
        request.setStartAt(newStart);

        when(appointmentRepository.findByIdAndBusinessId(100L, 1L)).thenReturn(Optional.of(appointment));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(3L, newStart, newStart.plusSeconds(1800), 100L,
                AppointmentStatus.CANCELLED)).thenReturn(false);
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        appointmentService.reschedule(100L, request);

        assertThat(appointment.getStartAt()).isEqualTo(newStart);
        assertThat(appointment.getEndAt()).isEqualTo(newStart.plusSeconds(1800));
    }

    @Test
    void reschedulesAppointmentUsingExplicitBusinessIdOverloadUsedByAiChannel() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);

        RescheduleAppointmentRequest request = new RescheduleAppointmentRequest();
        Instant newStart = startAt.plusSeconds(3600);
        request.setStartAt(newStart);

        when(appointmentRepository.findByIdAndBusinessId(100L, 1L)).thenReturn(Optional.of(appointment));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(3L, newStart, newStart.plusSeconds(1800), 100L,
                AppointmentStatus.CANCELLED)).thenReturn(false);
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        appointmentService.reschedule(1L, 100L, request);

        assertThat(appointment.getStartAt()).isEqualTo(newStart);
    }

    @Test
    void throwsWhenRescheduleCausesOverlap() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);

        RescheduleAppointmentRequest request = new RescheduleAppointmentRequest();
        Instant newStart = startAt.plusSeconds(3600);
        request.setStartAt(newStart);

        when(appointmentRepository.findByIdAndBusinessId(100L, 1L)).thenReturn(Optional.of(appointment));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(3L, newStart, newStart.plusSeconds(1800), 100L,
                AppointmentStatus.CANCELLED)).thenReturn(true);

        assertThatThrownBy(() -> appointmentService.reschedule(100L, request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void throwsWhenReschedulingCancelledAppointment() {
        Appointment appointment = existingAppointment(AppointmentStatus.CANCELLED);

        RescheduleAppointmentRequest request = new RescheduleAppointmentRequest();
        request.setStartAt(startAt.plusSeconds(3600));

        when(appointmentRepository.findByIdAndBusinessId(100L, 1L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> appointmentService.reschedule(100L, request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void cancelsAppointmentSuccessfully() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findByIdAndBusinessId(100L, 1L)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        appointmentService.cancel(100L);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(appointmentRepository).save(appointment);
    }

    @Test
    void cancelsAppointmentUsingExplicitBusinessIdOverloadUsedByAiChannel() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findByIdAndBusinessId(100L, 1L)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        appointmentService.cancel(1L, 100L);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancellingAlreadyCancelledAppointmentIsIdempotent() {
        Appointment appointment = existingAppointment(AppointmentStatus.CANCELLED);

        when(appointmentRepository.findByIdAndBusinessId(100L, 1L)).thenReturn(Optional.of(appointment));
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        appointmentService.cancel(100L);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void updatesStatusSuccessfully() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);

        UpdateAppointmentStatusRequest request = new UpdateAppointmentStatusRequest();
        request.setStatus(AppointmentStatus.COMPLETED);

        when(appointmentRepository.findByIdAndBusinessId(100L, 1L)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        appointmentService.updateStatus(100L, request);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void throwsWhenSettingStatusToPendingManually() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);

        UpdateAppointmentStatusRequest request = new UpdateAppointmentStatusRequest();
        request.setStatus(AppointmentStatus.PENDING);

        when(appointmentRepository.findByIdAndBusinessId(100L, 1L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> appointmentService.updateStatus(100L, request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void throwsAppointmentNotFoundWhenMissing() {
        when(appointmentRepository.findByIdAndBusinessId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.findById(999L))
                .isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void throwsAppointmentNotFoundWhenAppointmentBelongsToAnotherBusiness() {
        when(currentUserProvider.getCurrentBusinessId()).thenReturn(2L);
        when(appointmentRepository.findByIdAndBusinessId(100L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.findById(100L))
                .isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void listsAppointmentsWithoutFilters() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "startAt"));
        Page<Appointment> page = new PageImpl<>(List.of(appointment), pageable, 1);

        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        Page<AppointmentResponse> result = appointmentService.findAll(
                null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listsAppointmentsFilteredByEmployeeId() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);
        Page<Appointment> page = new PageImpl<>(List.of(appointment));

        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        Page<AppointmentResponse> result = appointmentService.findAll(
                3L, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(appointmentRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listsAppointmentsFilteredByCustomerId() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);
        Page<Appointment> page = new PageImpl<>(List.of(appointment));

        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        Page<AppointmentResponse> result = appointmentService.findAll(
                null, 2L, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listsAppointmentsFilteredByStatus() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);
        Page<Appointment> page = new PageImpl<>(List.of(appointment));

        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        Page<AppointmentResponse> result = appointmentService.findAll(
                null, null, AppointmentStatus.CONFIRMED, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listsAppointmentsFilteredOnlyByFrom() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);
        Page<Appointment> page = new PageImpl<>(List.of(appointment));

        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        Page<AppointmentResponse> result = appointmentService.findAll(
                null, null, null, startAt, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listsAppointmentsFilteredOnlyByTo() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);
        Page<Appointment> page = new PageImpl<>(List.of(appointment));

        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        Page<AppointmentResponse> result = appointmentService.findAll(
                null, null, null, null, endAt, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listsAppointmentsFilteredByFromAndTo() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);
        Page<Appointment> page = new PageImpl<>(List.of(appointment));

        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        Page<AppointmentResponse> result = appointmentService.findAll(
                null, null, null, startAt, endAt, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listsAppointmentsWithMultipleCombinedFilters() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);
        Page<Appointment> page = new PageImpl<>(List.of(appointment));

        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        Page<AppointmentResponse> result = appointmentService.findAll(
                3L, 2L, AppointmentStatus.CONFIRMED, startAt, endAt, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void throwsBadRequestWhenFromIsAfterTo() {
        assertThatThrownBy(() -> appointmentService.findAll(
                null, null, null, endAt, startAt, PageRequest.of(0, 10)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(appointmentRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listingAlwaysScopesSpecificationToAuthenticatedBusiness() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);
        Page<Appointment> page = new PageImpl<>(List.of(appointment));

        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        appointmentService.findAll(null, null, null, null, null, PageRequest.of(0, 10));

        verify(currentUserProvider).getCurrentBusinessId();
    }

    @Test
    void appliesDefaultSortByStartAtAscendingWhenPageableHasNoSort() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);
        Page<Appointment> page = new PageImpl<>(List.of(appointment));

        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        appointmentService.findAll(null, null, null, null, null, PageRequest.of(0, 10));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(appointmentRepository).findAll(any(Specification.class), pageableCaptor.capture());

        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("startAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void preservesExplicitSortWhenPageableAlreadyHasOne() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);
        Page<Appointment> page = new PageImpl<>(List.of(appointment));
        Pageable requestedPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "startAt"));

        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        appointmentService.findAll(null, null, null, null, null, requestedPageable);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(appointmentRepository).findAll(any(Specification.class), pageableCaptor.capture());

        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("startAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    // ---------------------------------------------------------------------------------------
    // isAvailable(businessId, employeeId, serviceId, startAt): única fuente de verdad de
    // disponibilidad, consultada por el flujo conversacional de WhatsApp sin crear ninguna cita.
    // Reusa internamente los mismos checks que create()/reschedule() (assertBookable), así que
    // consulta previa y revalidación final nunca pueden divergir.
    // ---------------------------------------------------------------------------------------

    @Test
    void isAvailableReturnsTrueWhenEmployeeIsFreeWithinScheduleAndNoOverlap() {
        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(any(), any(), any(), any(), any())).thenReturn(false);

        boolean available = appointmentService.isAvailable(1L, 3L, 4L, startAt);

        assertThat(available).isTrue();
    }

    @Test
    void isAvailableReturnsFalseWhenEmployeeHasOverlappingAppointment() {
        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(any(), any(), any(), any(), any())).thenReturn(true);

        boolean available = appointmentService.isAvailable(1L, 3L, 4L, startAt);

        assertThat(available).isFalse();
    }

    @Test
    void isAvailableReturnsFalseWhenOutsideWorkingHours() {
        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of());

        boolean available = appointmentService.isAvailable(1L, 3L, 4L, startAt);

        assertThat(available).isFalse();
    }

    @Test
    void isAvailableReturnsFalseWhenEmployeeCannotPerformService() {
        Service other = new Service();
        other.setId(9L);
        other.setName("Coloración");
        other.setDurationMinutes(60);
        other.setBusiness(business);

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(9L, 1L)).thenReturn(Optional.of(other));

        boolean available = appointmentService.isAvailable(1L, 3L, 9L, startAt);

        assertThat(available).isFalse();
    }

    @Test
    void isAvailableReturnsFalseWhenEmployeeOrServiceDoesNotExist() {
        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(99L, 1L)).thenReturn(Optional.empty());

        boolean available = appointmentService.isAvailable(1L, 99L, 4L, startAt);

        assertThat(available).isFalse();
    }

    @Test
    void isAvailableWithoutServiceIdUsesDefaultDurationAndSkipsEmployeeServiceCheck() {
        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(any(), any(), any(), any(), any())).thenReturn(false);

        boolean available = appointmentService.isAvailable(1L, 3L, null, startAt);

        assertThat(available).isTrue();
        verify(serviceRepository, never()).findByIdAndBusinessIdAndActiveTrue(any(), any());
    }

    // ---------------------------------------------------------------------------------------
    // checkAvailability(businessId, employeeId, serviceId, startAt): misma evaluación que
    // isAvailable, pero exponiendo el motivo exacto (AvailabilityReason) para que el flujo
    // conversacional pueda redactar mensajes específicos en vez de uno genérico.
    // ---------------------------------------------------------------------------------------

    @Test
    void checkAvailabilityReturnsAvailableWhenFreeWithinScheduleAndNoOverlap() {
        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(any(), any(), any(), any(), any())).thenReturn(false);

        AvailabilityCheck result = appointmentService.checkAvailability(1L, 3L, 4L, startAt);

        assertThat(result.available()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void checkAvailabilityReportsOverlappingReasonWhenEmployeeHasAnotherAppointment() {
        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(any(), any(), any(), any(), any())).thenReturn(true);

        AvailabilityCheck result = appointmentService.checkAvailability(1L, 3L, 4L, startAt);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(AvailabilityReason.OVERLAPPING);
    }

    @Test
    void checkAvailabilityReportsOutsideScheduleReasonWhenEmployeeHasNoScheduleThatDay() {
        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of());

        AvailabilityCheck result = appointmentService.checkAvailability(1L, 3L, 4L, startAt);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(AvailabilityReason.OUTSIDE_SCHEDULE);
    }

    @Test
    void checkAvailabilityReportsCannotPerformServiceReasonWhenEmployeeIsNotEnabledForService() {
        Service other = new Service();
        other.setId(9L);
        other.setName("Coloración");
        other.setDurationMinutes(60);
        other.setBusiness(business);

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(9L, 1L)).thenReturn(Optional.of(other));

        AvailabilityCheck result = appointmentService.checkAvailability(1L, 3L, 9L, startAt);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(AvailabilityReason.EMPLOYEE_CANNOT_PERFORM_SERVICE);
    }

    @Test
    void checkAvailabilityReportsEmployeeNotFoundReasonWhenEmployeeDoesNotExist() {
        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(99L, 1L)).thenReturn(Optional.empty());

        AvailabilityCheck result = appointmentService.checkAvailability(1L, 99L, 4L, startAt);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(AvailabilityReason.EMPLOYEE_NOT_FOUND);
    }

    // ---------------------------------------------------------------------------------------
    // findAvailableSlots(businessId, employeeId, serviceId, date, maxSlots): horarios REALES
    // (Schedule menos Appointments existentes, respetando la duración del Service), la misma
    // fuente de verdad que checkAvailability para cada candidato. Nunca deriva horarios del
    // Schedule bruto: cada candidato se valida individualmente.
    // ---------------------------------------------------------------------------------------

    @Test
    void findAvailableSlotsReturnsEveryFreeStartTimeWithinScheduleStep() {
        LocalDate futureMonday = LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(any(), any(), any(), any(), any())).thenReturn(false);

        List<Instant> slots = appointmentService.findAvailableSlots(1L, 3L, 4L, futureMonday, 10);

        // Schedule 08:00-12:00, Service de 30 minutos, paso de 30 minutos: 08:00, 08:30, ..., 11:30.
        List<LocalTime> expectedTimes = List.of(LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0),
                LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(10, 30), LocalTime.of(11, 0),
                LocalTime.of(11, 30));
        List<LocalTime> actualTimes = slots.stream().map(instant -> instant.atZone(ZONE).toLocalTime()).toList();
        assertThat(actualTimes).isEqualTo(expectedTimes);
    }

    @Test
    void findAvailableSlotsExcludesSlotOccupiedByExistingAppointment() {
        LocalDate futureMonday = LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Instant occupiedStart = ZonedDateTime.of(futureMonday, LocalTime.of(9, 0), ZONE).toInstant();
        Instant occupiedEnd = occupiedStart.plusSeconds(30 * 60);

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(any(), any(), any(), any(), any())).thenReturn(false);
        when(appointmentRepository.existsOverlapping(3L, occupiedStart, occupiedEnd, null, AppointmentStatus.CANCELLED))
                .thenReturn(true);

        List<Instant> slots = appointmentService.findAvailableSlots(1L, 3L, 4L, futureMonday, 10);

        assertThat(slots).doesNotContain(occupiedStart);
        assertThat(slots).contains(
                ZonedDateTime.of(futureMonday, LocalTime.of(8, 0), ZONE).toInstant(),
                ZonedDateTime.of(futureMonday, LocalTime.of(9, 30), ZONE).toInstant());
    }

    @Test
    void findAvailableSlotsRespectsRealServiceDurationNotDefaultThirtyMinutes() {
        Service longService = new Service();
        longService.setId(11L);
        longService.setName("Coloración");
        longService.setDurationMinutes(90);
        longService.setBusiness(business);
        employee.setServices(Set.of(longService));

        LocalDate futureMonday = LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(11L, 1L)).thenReturn(Optional.of(longService));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(any(), any(), any(), any(), any())).thenReturn(false);

        List<Instant> slots = appointmentService.findAvailableSlots(1L, 3L, 11L, futureMonday, 10);

        // Schedule 08:00-12:00 (240 min), Service de 90 minutos, paso de 30: 08:00, 08:30, 09:00
        // (09:00+90=10:30, cabe); 09:30+90=11:00 cabe; 10:30+90=12:00 cabe (límite exacto);
        // 11:00+90=12:30 NO cabe. Última hora válida: 10:30.
        List<LocalTime> actualTimes = slots.stream().map(instant -> instant.atZone(ZONE).toLocalTime()).toList();
        assertThat(actualTimes).contains(LocalTime.of(10, 30));
        assertThat(actualTimes).doesNotContain(LocalTime.of(11, 0));
    }

    @Test
    void findAvailableSlotsReturnsEmptyWhenEmployeeCannotPerformService() {
        Service other = new Service();
        other.setId(9L);
        other.setName("Coloración");
        other.setDurationMinutes(60);
        other.setBusiness(business);

        LocalDate futureMonday = LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(9L, 1L)).thenReturn(Optional.of(other));

        List<Instant> slots = appointmentService.findAvailableSlots(1L, 3L, 9L, futureMonday, 10);

        assertThat(slots).isEmpty();
    }

    @Test
    void findAvailableSlotsReturnsEmptyWhenEmployeeHasNoScheduleThatDay() {
        LocalDate futureMonday = LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of());

        List<Instant> slots = appointmentService.findAvailableSlots(1L, 3L, 4L, futureMonday, 10);

        assertThat(slots).isEmpty();
    }

    @Test
    void findAvailableSlotsNeverReturnsMoreThanRequestedMax() {
        LocalDate futureMonday = LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(3L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(4L, 1L)).thenReturn(Optional.of(service));
        when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(3L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.existsOverlapping(any(), any(), any(), any(), any())).thenReturn(false);

        List<Instant> slots = appointmentService.findAvailableSlots(1L, 3L, 4L, futureMonday, 3);

        assertThat(slots).hasSize(3);
    }

    private Appointment existingAppointment(AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setId(100L);
        appointment.setBusiness(business);
        appointment.setCustomer(customer);
        appointment.setEmployee(employee);
        appointment.setService(service);
        appointment.setStartAt(startAt);
        appointment.setEndAt(endAt);
        appointment.setStatus(status);
        return appointment;
    }

}
