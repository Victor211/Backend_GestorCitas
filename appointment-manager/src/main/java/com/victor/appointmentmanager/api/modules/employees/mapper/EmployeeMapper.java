package com.victor.appointmentmanager.api.modules.employees.mapper;

import com.victor.appointmentmanager.api.common.mapper.BaseMapper;
import com.victor.appointmentmanager.api.modules.employees.dto.request.CreateEmployeeRequest;
import com.victor.appointmentmanager.api.modules.employees.dto.request.UpdateEmployeeRequest;
import com.victor.appointmentmanager.api.modules.employees.dto.response.EmployeeResponse;
import com.victor.appointmentmanager.api.modules.employees.dto.response.EmployeeServiceResponse;
import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import com.victor.appointmentmanager.api.modules.services.entity.Service;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee, EmployeeResponse> {

    @Mapping(target = "businessId", source = "business.id")
    @Mapping(target = "services", source = "services")
    @Override
    EmployeeResponse toDto(Employee entity);

    EmployeeServiceResponse toEmployeeServiceResponse(Service service);

    @Mapping(target = "services", ignore = true)
    Employee toEntity(CreateEmployeeRequest request);

    @Mapping(target = "services", ignore = true)
    void updateEntityFromRequest(UpdateEmployeeRequest request, @MappingTarget Employee employee);

}
