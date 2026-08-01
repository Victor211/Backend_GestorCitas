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
import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private BusinessAccessValidator businessAccessValidator;

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
    }

    private CreateAppointmentRequest buildCreateRequest() {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setBusinessId(1L);
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
        when(customerRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndActiveTrue(4L)).thenReturn(Optional.of(service));
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
        when(customerRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndActiveTrue(4L)).thenReturn(Optional.of(service));
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
        when(customerRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndActiveTrue(4L)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void throwsWhenCustomerBelongsToAnotherBusiness() {
        Business otherBusiness = new Business();
        otherBusiness.setId(999L);
        customer.setBusiness(otherBusiness);

        CreateAppointmentRequest request = buildCreateRequest();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void throwsWhenEmployeeBelongsToAnotherBusiness() {
        Business otherBusiness = new Business();
        otherBusiness.setId(999L);
        employee.setBusiness(otherBusiness);

        CreateAppointmentRequest request = buildCreateRequest();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void throwsWhenServiceBelongsToAnotherBusiness() {
        Business otherBusiness = new Business();
        otherBusiness.setId(999L);
        service.setBusiness(otherBusiness);

        CreateAppointmentRequest request = buildCreateRequest();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndActiveTrue(4L)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void throwsWhenEmployeeIsNotEnabledForService() {
        employee.setServices(Set.of());

        CreateAppointmentRequest request = buildCreateRequest();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndActiveTrue(4L)).thenReturn(Optional.of(service));

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
        when(customerRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndActiveTrue(4L)).thenReturn(Optional.of(service));
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
        when(customerRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndActiveTrue(4L)).thenReturn(Optional.of(service));
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
        when(customerRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndActiveTrue(4L)).thenReturn(Optional.of(service));
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
        when(customerRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndActiveTrue(4L)).thenReturn(Optional.of(service));
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
        when(customerRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(customer));
        when(employeeRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findByIdAndActiveTrue(4L)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
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

        appointmentService.reschedule(100L, 1L, request);

        assertThat(appointment.getStartAt()).isEqualTo(newStart);
        assertThat(appointment.getEndAt()).isEqualTo(newStart.plusSeconds(1800));
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

        assertThatThrownBy(() -> appointmentService.reschedule(100L, 1L, request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void throwsWhenReschedulingCancelledAppointment() {
        Appointment appointment = existingAppointment(AppointmentStatus.CANCELLED);

        RescheduleAppointmentRequest request = new RescheduleAppointmentRequest();
        request.setStartAt(startAt.plusSeconds(3600));

        when(appointmentRepository.findByIdAndBusinessId(100L, 1L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> appointmentService.reschedule(100L, 1L, request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void cancelsAppointmentSuccessfully() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findByIdAndBusinessId(100L, 1L)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        appointmentService.cancel(100L, 1L);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(appointmentRepository).save(appointment);
    }

    @Test
    void cancellingAlreadyCancelledAppointmentIsIdempotent() {
        Appointment appointment = existingAppointment(AppointmentStatus.CANCELLED);

        when(appointmentRepository.findByIdAndBusinessId(100L, 1L)).thenReturn(Optional.of(appointment));
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        appointmentService.cancel(100L, 1L);

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

        appointmentService.updateStatus(100L, 1L, request);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void throwsWhenSettingStatusToPendingManually() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);

        UpdateAppointmentStatusRequest request = new UpdateAppointmentStatusRequest();
        request.setStatus(AppointmentStatus.PENDING);

        when(appointmentRepository.findByIdAndBusinessId(100L, 1L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> appointmentService.updateStatus(100L, 1L, request))
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void throwsAppointmentNotFoundWhenMissing() {
        when(appointmentRepository.findByIdAndBusinessId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.findById(999L, 1L))
                .isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void throwsBadRequestWhenBusinessIdMissingOnFindById() {
        assertThatThrownBy(() -> appointmentService.findById(999L, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void listsAppointmentsPaginatedWithFilters() {
        Appointment appointment = existingAppointment(AppointmentStatus.CONFIRMED);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Appointment> page = new PageImpl<>(List.of(appointment), pageable, 1);

        when(appointmentRepository.search(eq(1L), eq(3L), isNull(), eq(AppointmentStatus.CONFIRMED),
                any(), any(), eq(pageable))).thenReturn(page);
        when(appointmentMapper.toDto(appointment)).thenReturn(new AppointmentResponse());

        Page<AppointmentResponse> result = appointmentService.findAll(
                1L, 3L, null, AppointmentStatus.CONFIRMED, startAt, endAt, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
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
