package com.victor.appointmentmanager.api.modules.employees.mapper;

import com.victor.appointmentmanager.api.common.mapper.BaseMapper;
import com.victor.appointmentmanager.api.modules.employees.dto.request.CreateEmployeeRequest;
import com.victor.appointmentmanager.api.modules.employees.dto.request.UpdateEmployeeRequest;
import com.victor.appointmentmanager.api.modules.employees.dto.response.EmployeeResponse;
import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee, EmployeeResponse> {

    @Mapping(target = "businessId", source = "business.id")
    @Override
    EmployeeResponse toDto(Employee entity);

    Employee toEntity(CreateEmployeeRequest request);

    void updateEntityFromRequest(UpdateEmployeeRequest request, @MappingTarget Employee employee);

}
