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
import com.victor.appointmentmanager.api.modules.schedule.service.ScheduleService;
import com.victor.appointmentmanager.api.security.BusinessAccessValidator;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final EmployeeRepository employeeRepository;
    private final BusinessRepository businessRepository;
    private final ScheduleMapper scheduleMapper;
    private final BusinessAccessValidator businessAccessValidator;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public ScheduleResponse create(CreateScheduleRequest request) {
        Employee employee = findActiveEmployeeOrThrow(request.getEmployeeId());
        businessAccessValidator.validate(employee.getBusiness().getId());
        assertValidInterval(request.getStartTime(), request.getEndTime());
        assertNoOverlap(employee.getId(), request.getDayOfWeek(), request.getStartTime(), request.getEndTime(), null);

        Schedule schedule = scheduleMapper.toEntity(request);
        schedule.setEmployee(employee);

        Schedule saved = scheduleRepository.save(schedule);
        return scheduleMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ScheduleResponse update(Long id, UpdateScheduleRequest request) {
        Schedule schedule = findOwnedByIdOrThrow(id);

        assertValidInterval(request.getStartTime(), request.getEndTime());
        assertNoOverlap(schedule.getEmployee().getId(), request.getDayOfWeek(),
                request.getStartTime(), request.getEndTime(), id);

        scheduleMapper.updateEntityFromRequest(request, schedule);

        Schedule updated = scheduleRepository.save(schedule);
        return scheduleMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleResponse findById(Long id) {
        return scheduleMapper.toDto(findOwnedByIdOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleResponse> findAllByEmployee(Long employeeId) {
        Employee employee = findActiveEmployeeOrThrow(employeeId);
        businessAccessValidator.validate(employee.getBusiness().getId());

        return scheduleRepository.findByEmployeeIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(employeeId)
                .stream()
                .map(scheduleMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleResponse> findAllByBusiness(Long businessId, Long employeeId, DayOfWeek dayOfWeek) {
        if (businessId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "businessId es obligatorio");
        }
        businessAccessValidator.validate(businessId);

        businessRepository.findByIdAndActiveTrue(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con id " + businessId));

        return scheduleRepository.findActiveByBusiness(businessId, employeeId, dayOfWeek)
                .stream()
                .map(scheduleMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Schedule schedule = findOwnedByIdOrThrow(id);
        schedule.setActive(false);
        scheduleRepository.save(schedule);
    }

    private Schedule findOwnedByIdOrThrow(Long id) {
        return scheduleRepository.findByIdAndBusinessIdAndActiveTrue(id, currentUserProvider.getCurrentBusinessId())
                .orElseThrow(() -> new ScheduleNotFoundException("Horario no encontrado con id " + id));
    }

    private Employee findActiveEmployeeOrThrow(Long employeeId) {
        return employeeRepository.findByIdAndActiveTrue(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con id " + employeeId));
    }

    private void assertValidInterval(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new BusinessException("La hora de inicio debe ser anterior a la hora de finalización");
        }
    }

    private void assertNoOverlap(Long employeeId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
                                  Long excludeId) {
        if (scheduleRepository.existsOverlapping(employeeId, dayOfWeek, startTime, endTime, excludeId)) {
            throw new BusinessException("El horario se superpone con otro horario existente del empleado");
        }
    }

}
