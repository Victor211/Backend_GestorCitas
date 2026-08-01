package com.victor.appointmentmanager.api.modules.schedule.service;

import com.victor.appointmentmanager.api.modules.schedule.dto.request.CreateScheduleRequest;
import com.victor.appointmentmanager.api.modules.schedule.dto.request.UpdateScheduleRequest;
import com.victor.appointmentmanager.api.modules.schedule.dto.response.ScheduleResponse;

import java.time.DayOfWeek;
import java.util.List;

public interface ScheduleService {

    ScheduleResponse create(CreateScheduleRequest request);

    ScheduleResponse update(Long id, UpdateScheduleRequest request);

    ScheduleResponse findById(Long id);

    List<ScheduleResponse> findAllByEmployee(Long employeeId);

    List<ScheduleResponse> findAllByBusiness(Long businessId, Long employeeId, DayOfWeek dayOfWeek);

    void delete(Long id);

}
