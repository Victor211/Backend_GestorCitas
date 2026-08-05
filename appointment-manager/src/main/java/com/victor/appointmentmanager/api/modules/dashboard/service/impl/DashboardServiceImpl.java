package com.victor.appointmentmanager.api.modules.dashboard.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;
import com.victor.appointmentmanager.api.modules.appointments.entity.Appointment;
import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
import com.victor.appointmentmanager.api.modules.appointments.repository.AppointmentRepository;
import com.victor.appointmentmanager.api.modules.customers.repository.CustomerRepository;
import com.victor.appointmentmanager.api.modules.dashboard.dto.response.DashboardResponse;
import com.victor.appointmentmanager.api.modules.dashboard.mapper.DashboardMapper;
import com.victor.appointmentmanager.api.modules.dashboard.service.DashboardService;
import com.victor.appointmentmanager.api.modules.employees.repository.EmployeeRepository;
import com.victor.appointmentmanager.api.modules.services.repository.ServiceRepository;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int UPCOMING_APPOINTMENTS_LIMIT = 5;

    private static final List<AppointmentStatus> ACTIVE_APPOINTMENT_STATUSES =
            List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);

    private final BusinessRepository businessRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final EmployeeRepository employeeRepository;
    private final ServiceRepository serviceRepository;
    private final DashboardMapper dashboardMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        Long businessId = currentUserProvider.getCurrentBusinessId();
        Business business = findActiveBusinessOrThrow(businessId);
        ZoneId zoneId = resolveZoneId(business);

        LocalDate today = LocalDate.now(zoneId);
        Instant startOfDay = today.atStartOfDay(zoneId).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(zoneId).toInstant();

        long todayAppointments = appointmentRepository
                .countByBusinessIdAndStartAtGreaterThanEqualAndStartAtLessThanAndStatusIn(
                        businessId, startOfDay, endOfDay, ACTIVE_APPOINTMENT_STATUSES);

        long activeCustomers = customerRepository.countByBusinessIdAndActiveTrue(businessId);
        long activeEmployees = employeeRepository.countByBusinessIdAndActiveTrue(businessId);
        long activeServices = serviceRepository.countByBusinessIdAndActiveTrue(businessId);

        List<Appointment> upcomingAppointments = findUpcomingAppointments(businessId);

        DashboardResponse response = new DashboardResponse();
        response.setTodayAppointments(todayAppointments);
        response.setActiveCustomers(activeCustomers);
        response.setActiveEmployees(activeEmployees);
        response.setActiveServices(activeServices);
        response.setUpcomingAppointments(dashboardMapper.toUpcomingAppointmentResponseList(upcomingAppointments));
        return response;
    }

    private List<Appointment> findUpcomingAppointments(Long businessId) {
        Pageable limit = PageRequest.of(0, UPCOMING_APPOINTMENTS_LIMIT, Sort.by(Sort.Direction.ASC, "startAt"));
        return appointmentRepository.findByBusinessIdAndStartAtGreaterThanEqualAndStatusInOrderByStartAtAsc(
                businessId, Instant.now(), ACTIVE_APPOINTMENT_STATUSES, limit);
    }

    private Business findActiveBusinessOrThrow(Long businessId) {
        return businessRepository.findByIdAndActiveTrue(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con id " + businessId));
    }

    private ZoneId resolveZoneId(Business business) {
        try {
            return ZoneId.of(business.getTimezone());
        } catch (DateTimeException ex) {
            throw new BusinessException("La zona horaria configurada en el negocio no es válida");
        }
    }

}
