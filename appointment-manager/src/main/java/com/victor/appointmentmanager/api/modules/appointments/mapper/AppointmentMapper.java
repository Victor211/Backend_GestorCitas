package com.victor.appointmentmanager.api.modules.appointments.mapper;

import com.victor.appointmentmanager.api.common.mapper.BaseMapper;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.CreateAppointmentRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.response.AppointmentResponse;
import com.victor.appointmentmanager.api.modules.appointments.entity.Appointment;
import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment, AppointmentResponse> {

    @Mapping(target = "businessId", source = "business.id")
    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", source = "customer", qualifiedByName = "toCustomerFullName")
    @Mapping(target = "employeeId", source = "employee.id")
    @Mapping(target = "employeeName", source = "employee", qualifiedByName = "toEmployeeFullName")
    @Mapping(target = "serviceId", source = "service.id")
    @Mapping(target = "serviceName", source = "service.name")
    @Mapping(target = "serviceDurationMinutes", source = "service.durationMinutes")
    @Mapping(target = "servicePrice", source = "service.price")
    @Override
    AppointmentResponse toDto(Appointment entity);

    Appointment toEntity(CreateAppointmentRequest request);

    @Named("toCustomerFullName")
    default String toCustomerFullName(Customer customer) {
        if (customer == null) {
            return null;
        }
        String firstName = customer.getFirstName() != null ? customer.getFirstName() : "";
        String lastName = customer.getLastName() != null ? customer.getLastName() : "";
        return (firstName + " " + lastName).trim();
    }

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
