package com.victor.appointmentmanager.api.modules.schedule.mapper;

import com.victor.appointmentmanager.api.common.mapper.BaseMapper;
import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import com.victor.appointmentmanager.api.modules.schedule.dto.request.CreateScheduleRequest;
import com.victor.appointmentmanager.api.modules.schedule.dto.request.UpdateScheduleRequest;
import com.victor.appointmentmanager.api.modules.schedule.dto.response.ScheduleResponse;
import com.victor.appointmentmanager.api.modules.schedule.entity.Schedule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule, ScheduleResponse> {

    @Mapping(target = "employeeId", source = "employee.id")
    @Mapping(target = "employeeName", source = "employee", qualifiedByName = "toEmployeeFullName")
    @Override
    ScheduleResponse toDto(Schedule entity);

    Schedule toEntity(CreateScheduleRequest request);

    void updateEntityFromRequest(UpdateScheduleRequest request, @MappingTarget Schedule schedule);

    @Named("toEmployeeFullName")
    default String toEmployeeFullName(Employee employee) {
        if (employee == null) {
            return null;
        }
        String firstName = employee.getFirstName() != null ? employee.getFirstName() : "";
        String lastName = employee.getLastName() != null ? employee.getLastName() : "";
        return (firstName + " " + lastName).trim();
    }

}
