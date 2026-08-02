package com.victor.appointmentmanager.api.modules.employees.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;
import com.victor.appointmentmanager.api.modules.employees.dto.request.CreateEmployeeRequest;
import com.victor.appointmentmanager.api.modules.employees.dto.request.UpdateEmployeeRequest;
import com.victor.appointmentmanager.api.modules.employees.dto.response.EmployeeResponse;
import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import com.victor.appointmentmanager.api.modules.employees.mapper.EmployeeMapper;
import com.victor.appointmentmanager.api.modules.employees.repository.EmployeeRepository;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    private Business business;
    private Employee employee;

    @BeforeEach
    void setUp() {
        business = new Business();
        business.setId(1L);
        business.setName("Barbería Central");

        employee = new Employee();
        employee.setId(5L);
        employee.setFirstName("Juan");
        employee.setLastName("Pérez");
        employee.setPhone("0981000000");
        employee.setColor("#3B82F6");
        employee.setBusiness(business);
        employee.setActive(true);

        lenient().when(currentUserProvider.getCurrentBusinessId()).thenReturn(1L);
    }

    private CreateEmployeeRequest buildCreateRequest() {
        CreateEmployeeRequest request = new CreateEmployeeRequest();
        request.setFirstName("Juan");
        request.setLastName("Pérez");
        request.setPhone("0981000000");
        request.setColor("#3B82F6");
        return request;
    }

    @Test
    void createsEmployeeUsingBusinessIdFromJwt() {
        CreateEmployeeRequest request = buildCreateRequest();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.existsByPhoneAndBusinessId("0981000000", 1L)).thenReturn(false);
        when(employeeMapper.toEntity(request)).thenReturn(employee);
        when(employeeRepository.save(employee)).thenReturn(employee);

        EmployeeResponse response = new EmployeeResponse();
        response.setId(5L);
        when(employeeMapper.toDto(employee)).thenReturn(response);

        EmployeeResponse result = employeeService.create(request);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(employee.getBusiness()).isEqualTo(business);
        verify(employeeRepository).save(employee);
    }

    @Test
    void throwsBusinessExceptionWhenPhoneAlreadyExistsInBusinessOnCreate() {
        CreateEmployeeRequest request = buildCreateRequest();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.existsByPhoneAndBusinessId("0981000000", 1L)).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void updatesEmployeeSuccessfully() {
        UpdateEmployeeRequest request = new UpdateEmployeeRequest();
        request.setFirstName("Juan Carlos");
        request.setLastName("Pérez");
        request.setPhone("0981000000");
        request.setColor("#000000");

        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);

        EmployeeResponse response = new EmployeeResponse();
        response.setFirstName("Juan Carlos");
        when(employeeMapper.toDto(employee)).thenReturn(response);

        EmployeeResponse result = employeeService.update(5L, request);

        assertThat(result.getFirstName()).isEqualTo("Juan Carlos");
        verify(employeeMapper).updateEntityFromRequest(request, employee);
        verify(employeeRepository, never()).existsByPhoneAndBusinessId(any(), any());
    }

    @Test
    void throwsResourceNotFoundWhenUpdatingEmployeeOfAnotherBusiness() {
        UpdateEmployeeRequest request = new UpdateEmployeeRequest();
        request.setFirstName("Juan Carlos");
        request.setLastName("Pérez");
        request.setPhone("0981000000");
        request.setColor("#000000");

        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.update(5L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void throwsResourceNotFoundWhenFindingEmployeeOfAnotherBusiness() {
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.findById(5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void softDeletesEmployee() {
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(employee)).thenReturn(employee);

        employeeService.delete(5L);

        assertThat(employee.getActive()).isFalse();
        verify(employeeRepository).save(employee);
    }

    @Test
    void throwsResourceNotFoundWhenDeletingEmployeeOfAnotherBusiness() {
        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.delete(5L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(employeeRepository, never()).save(any());
    }

}
