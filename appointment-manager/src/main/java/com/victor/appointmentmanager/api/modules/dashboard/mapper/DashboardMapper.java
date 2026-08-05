package com.victor.appointmentmanager.api.modules.dashboard.mapper;

import com.victor.appointmentmanager.api.modules.appointments.entity.Appointment;
import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import com.victor.appointmentmanager.api.modules.dashboard.dto.response.UpcomingAppointmentResponse;
import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper
public interface DashboardMapper {

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", source = "customer", qualifiedByName = "toCustomerFullName")
    @Mapping(target = "employeeId", source = "employee.id")
    @Mapping(target = "employeeName", source = "employee", qualifiedByName = "toEmployeeFullName")
    @Mapping(target = "serviceId", source = "service.id")
    @Mapping(target = "serviceName", source = "service.name")
    UpcomingAppointmentResponse toUpcomingAppointmentResponse(Appointment appointment);

    List<UpcomingAppointmentResponse> toUpcomingAppointmentResponseList(List<Appointment> appointments);

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
