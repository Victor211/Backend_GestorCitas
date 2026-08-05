package com.victor.appointmentmanager.api.modules.dashboard.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.modules.appointments.entity.Appointment;
import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
import com.victor.appointmentmanager.api.modules.appointments.repository.AppointmentRepository;
import com.victor.appointmentmanager.api.modules.customers.repository.CustomerRepository;
import com.victor.appointmentmanager.api.modules.dashboard.dto.response.DashboardResponse;
import com.victor.appointmentmanager.api.modules.dashboard.dto.response.UpcomingAppointmentResponse;
import com.victor.appointmentmanager.api.modules.dashboard.mapper.DashboardMapper;
import com.victor.appointmentmanager.api.modules.employees.repository.EmployeeRepository;
import com.victor.appointmentmanager.api.modules.services.repository.ServiceRepository;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    private static final List<AppointmentStatus> ACTIVE_STATUSES =
            List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private DashboardMapper dashboardMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private DashboardServiceImpl dashboardService;

    private Business business;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardServiceImpl(businessRepository, appointmentRepository, customerRepository,
                employeeRepository, serviceRepository, dashboardMapper, currentUserProvider);

        business = new Business();
        business.setId(1L);
        business.setName("Barbería Central");
        business.setTimezone("America/Asuncion");

        lenient().when(currentUserProvider.getCurrentBusinessId()).thenReturn(1L);
        lenient().when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
    }

    private void mockZeroCounts() {
        when(appointmentRepository.countByBusinessIdAndStartAtGreaterThanEqualAndStartAtLessThanAndStatusIn(
                eq(1L), any(), any(), anyList())).thenReturn(0L);
        when(customerRepository.countByBusinessIdAndActiveTrue(1L)).thenReturn(0L);
        when(employeeRepository.countByBusinessIdAndActiveTrue(1L)).thenReturn(0L);
        when(serviceRepository.countByBusinessIdAndActiveTrue(1L)).thenReturn(0L);
        when(appointmentRepository.findByBusinessIdAndStartAtGreaterThanEqualAndStatusInOrderByStartAtAsc(
                eq(1L), any(), anyList(), any())).thenReturn(List.of());
        when(dashboardMapper.toUpcomingAppointmentResponseList(List.of())).thenReturn(List.of());
    }

    @Test
    void returnsCorrectCounts() {
        when(appointmentRepository.countByBusinessIdAndStartAtGreaterThanEqualAndStartAtLessThanAndStatusIn(
                eq(1L), any(), any(), anyList())).thenReturn(3L);
        when(customerRepository.countByBusinessIdAndActiveTrue(1L)).thenReturn(12L);
        when(employeeRepository.countByBusinessIdAndActiveTrue(1L)).thenReturn(4L);
        when(serviceRepository.countByBusinessIdAndActiveTrue(1L)).thenReturn(6L);

        Appointment upcoming = new Appointment();
        when(appointmentRepository.findByBusinessIdAndStartAtGreaterThanEqualAndStatusInOrderByStartAtAsc(
                eq(1L), any(), anyList(), any())).thenReturn(List.of(upcoming));
        UpcomingAppointmentResponse upcomingResponse = new UpcomingAppointmentResponse();
        when(dashboardMapper.toUpcomingAppointmentResponseList(List.of(upcoming)))
                .thenReturn(List.of(upcomingResponse));

        DashboardResponse response = dashboardService.getDashboard();

        assertThat(response.getTodayAppointments()).isEqualTo(3L);
        assertThat(response.getActiveCustomers()).isEqualTo(12L);
        assertThat(response.getActiveEmployees()).isEqualTo(4L);
        assertThat(response.getActiveServices()).isEqualTo(6L);
        assertThat(response.getUpcomingAppointments()).containsExactly(upcomingResponse);
    }

    @Test
    void scopesAllQueriesToAuthenticatedBusiness() {
        when(currentUserProvider.getCurrentBusinessId()).thenReturn(7L);
        Business otherBusiness = new Business();
        otherBusiness.setId(7L);
        otherBusiness.setTimezone("America/Asuncion");
        when(businessRepository.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(otherBusiness));

        when(appointmentRepository.countByBusinessIdAndStartAtGreaterThanEqualAndStartAtLessThanAndStatusIn(
                eq(7L), any(), any(), anyList())).thenReturn(0L);
        when(customerRepository.countByBusinessIdAndActiveTrue(7L)).thenReturn(0L);
        when(employeeRepository.countByBusinessIdAndActiveTrue(7L)).thenReturn(0L);
        when(serviceRepository.countByBusinessIdAndActiveTrue(7L)).thenReturn(0L);
        when(appointmentRepository.findByBusinessIdAndStartAtGreaterThanEqualAndStatusInOrderByStartAtAsc(
                eq(7L), any(), anyList(), any())).thenReturn(List.of());
        when(dashboardMapper.toUpcomingAppointmentResponseList(List.of())).thenReturn(List.of());

        dashboardService.getDashboard();

        verify(customerRepository).countByBusinessIdAndActiveTrue(7L);
        verify(employeeRepository).countByBusinessIdAndActiveTrue(7L);
        verify(serviceRepository).countByBusinessIdAndActiveTrue(7L);
        verify(customerRepository, never()).countByBusinessIdAndActiveTrue(1L);
    }

    @Test
    void calculatesTodayWindowUsingBusinessTimezone() {
        ZoneId zone = ZoneId.of("America/Asuncion");
        LocalDate today = LocalDate.now(zone);
        Instant expectedStart = today.atStartOfDay(zone).toInstant();
        Instant expectedEnd = today.plusDays(1).atStartOfDay(zone).toInstant();

        mockZeroCounts();

        dashboardService.getDashboard();

        verify(appointmentRepository).countByBusinessIdAndStartAtGreaterThanEqualAndStartAtLessThanAndStatusIn(
                1L, expectedStart, expectedEnd, ACTIVE_STATUSES);
    }

    @Test
    void todayCountOnlyConsidersPendingAndConfirmedStatuses() {
        mockZeroCounts();

        dashboardService.getDashboard();

        ArgumentCaptor<List<AppointmentStatus>> statusesCaptor = ArgumentCaptor.forClass(List.class);
        verify(appointmentRepository).countByBusinessIdAndStartAtGreaterThanEqualAndStartAtLessThanAndStatusIn(
                eq(1L), any(), any(), statusesCaptor.capture());

        assertThat(statusesCaptor.getValue())
                .containsExactlyInAnyOrder(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED)
                .doesNotContain(AppointmentStatus.CANCELLED, AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW);
    }

    @Test
    void upcomingAppointmentsOnlyConsidersFuturePendingAndConfirmed() {
        mockZeroCounts();

        Instant before = Instant.now();
        dashboardService.getDashboard();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<List<AppointmentStatus>> statusesCaptor = ArgumentCaptor.forClass(List.class);
        verify(appointmentRepository).findByBusinessIdAndStartAtGreaterThanEqualAndStatusInOrderByStartAtAsc(
                eq(1L), fromCaptor.capture(), statusesCaptor.capture(), any());

        assertThat(fromCaptor.getValue()).isBetween(before, after);
        assertThat(statusesCaptor.getValue())
                .containsExactlyInAnyOrder(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);
    }

    @Test
    void ordersUpcomingAppointmentsByStartAtAscending() {
        mockZeroCounts();

        dashboardService.getDashboard();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(appointmentRepository).findByBusinessIdAndStartAtGreaterThanEqualAndStatusInOrderByStartAtAsc(
                eq(1L), any(), anyList(), pageableCaptor.capture());

        org.springframework.data.domain.Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("startAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(org.springframework.data.domain.Sort.Direction.ASC);
    }

    @Test
    void limitsUpcomingAppointmentsToFive() {
        mockZeroCounts();

        dashboardService.getDashboard();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(appointmentRepository).findByBusinessIdAndStartAtGreaterThanEqualAndStatusInOrderByStartAtAsc(
                eq(1L), any(), anyList(), pageableCaptor.capture());

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void businessWithoutDataReturnsZerosAndEmptyList() {
        mockZeroCounts();

        DashboardResponse response = dashboardService.getDashboard();

        assertThat(response.getTodayAppointments()).isZero();
        assertThat(response.getActiveCustomers()).isZero();
        assertThat(response.getActiveEmployees()).isZero();
        assertThat(response.getActiveServices()).isZero();
        assertThat(response.getUpcomingAppointments()).isEmpty();
    }

    @Test
    void throwsBusinessExceptionWhenTimezoneIsInvalid() {
        business.setTimezone("Not/AValidZone");

        assertThatThrownBy(() -> dashboardService.getDashboard())
                .isInstanceOf(BusinessException.class);

        verify(appointmentRepository, never())
                .countByBusinessIdAndStartAtGreaterThanEqualAndStartAtLessThanAndStatusIn(
                        any(), any(), any(), anyList());
    }

}
