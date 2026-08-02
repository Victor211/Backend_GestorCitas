package com.victor.appointmentmanager.api.modules.schedule.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;
import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import com.victor.appointmentmanager.api.modules.employees.repository.EmployeeRepository;
import com.victor.appointmentmanager.api.modules.schedule.dto.request.CreateScheduleRequest;
import com.victor.appointmentmanager.api.modules.schedule.dto.request.UpdateScheduleRequest;
import com.victor.appointmentmanager.api.modules.schedule.dto.response.ScheduleResponse;
import com.victor.appointmentmanager.api.modules.schedule.entity.Schedule;
import com.victor.appointmentmanager.api.modules.schedule.exception.ScheduleNotFoundException;
import com.victor.appointmentmanager.api.modules.schedule.mapper.ScheduleMapper;
import com.victor.appointmentmanager.api.modules.schedule.repository.ScheduleRepository;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private ScheduleMapper scheduleMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private ScheduleServiceImpl scheduleService;

    private Business business;
    private Employee employee;
    private Schedule schedule;

    @BeforeEach
    void setUp() {
        business = new Business();
        business.setId(1L);
        business.setName("Barbería Central");

        employee = new Employee();
        employee.setId(5L);
        employee.setFirstName("Juan");
        employee.setLastName("Pérez");
        employee.setBusiness(business);

        schedule = new Schedule();
        schedule.setId(20L);
        schedule.setEmployee(employee);
        schedule.setDayOfWeek(DayOfWeek.MONDAY);
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(12, 0));
        schedule.setActive(true);

        lenient().when(currentUserProvider.getCurrentBusinessId()).thenReturn(1L);
    }

    @Test
    void createsScheduleSuccessfully() {
        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setEmployeeId(5L);
        request.setDayOfWeek(DayOfWeek.MONDAY);
        request.setStartTime(LocalTime.of(8, 0));
        request.setEndTime(LocalTime.of(12, 0));

        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.of(employee));
        when(scheduleRepository.existsOverlapping(5L, DayOfWeek.MONDAY,
                request.getStartTime(), request.getEndTime(), null)).thenReturn(false);
        when(scheduleMapper.toEntity(request)).thenReturn(schedule);
        when(scheduleRepository.save(schedule)).thenReturn(schedule);

        ScheduleResponse response = new ScheduleResponse();
        response.setId(20L);
        when(scheduleMapper.toDto(schedule)).thenReturn(response);

        ScheduleResponse result = scheduleService.create(request);

        assertThat(result.getId()).isEqualTo(20L);
        assertThat(schedule.getEmployee()).isEqualTo(employee);
        verify(scheduleRepository).save(schedule);
    }

    @Test
    void throwsWhenEmployeeDoesNotExist() {
        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setEmployeeId(999L);
        request.setDayOfWeek(DayOfWeek.MONDAY);
        request.setStartTime(LocalTime.of(8, 0));
        request.setEndTime(LocalTime.of(12, 0));

        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void throwsWhenEmployeeBelongsToAnotherBusiness() {
        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setEmployeeId(5L);
        request.setDayOfWeek(DayOfWeek.MONDAY);
        request.setStartTime(LocalTime.of(8, 0));
        request.setEndTime(LocalTime.of(12, 0));

        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void throwsWhenStartTimeEqualsEndTime() {
        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setEmployeeId(5L);
        request.setDayOfWeek(DayOfWeek.MONDAY);
        request.setStartTime(LocalTime.of(8, 0));
        request.setEndTime(LocalTime.of(8, 0));

        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> scheduleService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void throwsWhenStartTimeAfterEndTime() {
        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setEmployeeId(5L);
        request.setDayOfWeek(DayOfWeek.MONDAY);
        request.setStartTime(LocalTime.of(14, 0));
        request.setEndTime(LocalTime.of(10, 0));

        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> scheduleService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void throwsWhenOverlapsWithAnotherSchedule() {
        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setEmployeeId(5L);
        request.setDayOfWeek(DayOfWeek.MONDAY);
        request.setStartTime(LocalTime.of(10, 0));
        request.setEndTime(LocalTime.of(14, 0));

        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.of(employee));
        when(scheduleRepository.existsOverlapping(5L, DayOfWeek.MONDAY,
                request.getStartTime(), request.getEndTime(), null)).thenReturn(true);

        assertThatThrownBy(() -> scheduleService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void allowsContiguousIntervals() {
        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setEmployeeId(5L);
        request.setDayOfWeek(DayOfWeek.MONDAY);
        request.setStartTime(LocalTime.of(12, 0));
        request.setEndTime(LocalTime.of(16, 0));

        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.of(employee));
        when(scheduleRepository.existsOverlapping(5L, DayOfWeek.MONDAY,
                request.getStartTime(), request.getEndTime(), null)).thenReturn(false);
        when(scheduleMapper.toEntity(request)).thenReturn(schedule);
        when(scheduleRepository.save(schedule)).thenReturn(schedule);
        when(scheduleMapper.toDto(schedule)).thenReturn(new ScheduleResponse());

        assertThatCode(() -> scheduleService.create(request)).doesNotThrowAnyException();

        verify(scheduleRepository).save(schedule);
    }

    @Test
    void updatesScheduleSuccessfully() {
        UpdateScheduleRequest request = new UpdateScheduleRequest();
        request.setDayOfWeek(DayOfWeek.TUESDAY);
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(13, 0));

        when(scheduleRepository.findByIdAndBusinessIdAndActiveTrue(20L, 1L)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.existsOverlapping(5L, DayOfWeek.TUESDAY,
                request.getStartTime(), request.getEndTime(), 20L)).thenReturn(false);
        when(scheduleRepository.save(schedule)).thenReturn(schedule);

        ScheduleResponse response = new ScheduleResponse();
        response.setDayOfWeek(DayOfWeek.TUESDAY);
        when(scheduleMapper.toDto(schedule)).thenReturn(response);

        ScheduleResponse result = scheduleService.update(20L, request);

        assertThat(result.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        verify(scheduleMapper).updateEntityFromRequest(request, schedule);
    }

    @Test
    void throwsWhenUpdateOverlapsWithAnotherSchedule() {
        UpdateScheduleRequest request = new UpdateScheduleRequest();
        request.setDayOfWeek(DayOfWeek.MONDAY);
        request.setStartTime(LocalTime.of(10, 0));
        request.setEndTime(LocalTime.of(14, 0));

        when(scheduleRepository.findByIdAndBusinessIdAndActiveTrue(20L, 1L)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.existsOverlapping(5L, DayOfWeek.MONDAY,
                request.getStartTime(), request.getEndTime(), 20L)).thenReturn(true);

        assertThatThrownBy(() -> scheduleService.update(20L, request))
                .isInstanceOf(BusinessException.class);

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void throwsScheduleNotFoundWhenMissing() {
        when(scheduleRepository.findByIdAndBusinessIdAndActiveTrue(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.findById(999L))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    @Test
    void softDeletesSchedule() {
        when(scheduleRepository.findByIdAndBusinessIdAndActiveTrue(20L, 1L)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.save(schedule)).thenReturn(schedule);

        scheduleService.delete(20L);

        assertThat(schedule.getActive()).isFalse();
        verify(scheduleRepository).save(schedule);
    }

    @Test
    void listsSchedulesByEmployee() {
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.of(employee));
        when(scheduleRepository.findByEmployeeIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(5L))
                .thenReturn(List.of(schedule));

        ScheduleResponse response = new ScheduleResponse();
        response.setId(20L);
        when(scheduleMapper.toDto(schedule)).thenReturn(response);

        List<ScheduleResponse> result = scheduleService.findAllByEmployee(5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(20L);
    }

    @Test
    void throwsWhenListingSchedulesForEmployeeOfAnotherBusiness() {
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.findAllByEmployee(5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listsSchedulesByBusinessUsingCurrentBusinessId() {
        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(scheduleRepository.findActiveByBusiness(1L, null, null)).thenReturn(List.of(schedule));

        ScheduleResponse response = new ScheduleResponse();
        response.setId(20L);
        when(scheduleMapper.toDto(schedule)).thenReturn(response);

        List<ScheduleResponse> result = scheduleService.findAllByBusiness(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(20L);
    }

}
