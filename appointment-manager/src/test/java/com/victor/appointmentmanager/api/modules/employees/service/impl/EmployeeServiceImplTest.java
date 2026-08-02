package com.victor.appointmentmanager.api.modules.employees.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;
import com.victor.appointmentmanager.api.modules.employees.dto.request.CreateEmployeeRequest;
import com.victor.appointmentmanager.api.modules.employees.dto.request.UpdateEmployeeRequest;
import com.victor.appointmentmanager.api.modules.employees.dto.response.EmployeeResponse;
import com.victor.appointmentmanager.api.modules.employees.dto.response.EmployeeServiceResponse;
import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import com.victor.appointmentmanager.api.modules.employees.mapper.EmployeeMapper;
import com.victor.appointmentmanager.api.modules.employees.repository.EmployeeRepository;
import com.victor.appointmentmanager.api.modules.services.entity.Service;
import com.victor.appointmentmanager.api.modules.services.repository.ServiceRepository;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private ServiceRepository serviceRepository;

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    private Business business;
    private Employee employee;
    private Service serviceA;
    private Service serviceB;

    @BeforeEach
    void setUp() {
        business = new Business();
        business.setId(1L);
        business.setName("Barbería Central");

        serviceA = new Service();
        serviceA.setId(4L);
        serviceA.setName("Corte");
        serviceA.setDurationMinutes(30);
        serviceA.setPrice(new BigDecimal("15.00"));
        serviceA.setColor("#3B82F6");
        serviceA.setBusiness(business);
        serviceA.setActive(true);

        serviceB = new Service();
        serviceB.setId(6L);
        serviceB.setName("Barba");
        serviceB.setDurationMinutes(20);
        serviceB.setPrice(new BigDecimal("10.00"));
        serviceB.setColor("#000000");
        serviceB.setBusiness(business);
        serviceB.setActive(true);

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

    private CreateEmployeeRequest buildCreateRequest(Set<Long> serviceIds) {
        CreateEmployeeRequest request = new CreateEmployeeRequest();
        request.setFirstName("Juan");
        request.setLastName("Pérez");
        request.setPhone("0981000000");
        request.setColor("#3B82F6");
        request.setServiceIds(serviceIds);
        return request;
    }

    @Test
    void createsEmployeeSuccessfullyWithOneService() {
        CreateEmployeeRequest request = buildCreateRequest(Set.of(4L));

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.existsByPhoneAndBusinessId("0981000000", 1L)).thenReturn(false);
        when(serviceRepository.findAllByIdInAndBusinessIdAndActiveTrue(Set.of(4L), 1L))
                .thenReturn(List.of(serviceA));
        when(employeeMapper.toEntity(request)).thenReturn(employee);
        when(employeeRepository.save(employee)).thenReturn(employee);

        EmployeeResponse response = new EmployeeResponse();
        response.setId(5L);
        when(employeeMapper.toDto(employee)).thenReturn(response);

        EmployeeResponse result = employeeService.create(request);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(employee.getBusiness()).isEqualTo(business);
        assertThat(employee.getServices()).containsExactly(serviceA);
        verify(employeeRepository).save(employee);
    }

    @Test
    void createsEmployeeSuccessfullyWithMultipleServices() {
        CreateEmployeeRequest request = buildCreateRequest(Set.of(4L, 6L));

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.existsByPhoneAndBusinessId("0981000000", 1L)).thenReturn(false);
        when(serviceRepository.findAllByIdInAndBusinessIdAndActiveTrue(Set.of(4L, 6L), 1L))
                .thenReturn(List.of(serviceA, serviceB));
        when(employeeMapper.toEntity(request)).thenReturn(employee);
        when(employeeRepository.save(employee)).thenReturn(employee);
        when(employeeMapper.toDto(employee)).thenReturn(new EmployeeResponse());

        employeeService.create(request);

        assertThat(employee.getServices()).containsExactlyInAnyOrder(serviceA, serviceB);
    }

    @Test
    void throwsBusinessExceptionWhenPhoneAlreadyExistsInBusinessOnCreate() {
        CreateEmployeeRequest request = buildCreateRequest(Set.of(4L));

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.existsByPhoneAndBusinessId("0981000000", 1L)).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(employeeRepository, never()).save(any());
        verify(serviceRepository, never()).findAllByIdInAndBusinessIdAndActiveTrue(any(), any());
    }

    @Test
    void throwsResourceNotFoundWhenServiceDoesNotExistOnCreate() {
        CreateEmployeeRequest request = buildCreateRequest(Set.of(4L, 999L));

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.existsByPhoneAndBusinessId("0981000000", 1L)).thenReturn(false);
        when(serviceRepository.findAllByIdInAndBusinessIdAndActiveTrue(Set.of(4L, 999L), 1L))
                .thenReturn(List.of(serviceA));

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void throwsResourceNotFoundWhenServiceIsInactiveOnCreate() {
        CreateEmployeeRequest request = buildCreateRequest(Set.of(4L));

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.existsByPhoneAndBusinessId("0981000000", 1L)).thenReturn(false);
        when(serviceRepository.findAllByIdInAndBusinessIdAndActiveTrue(Set.of(4L), 1L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void throwsResourceNotFoundWhenServiceBelongsToAnotherBusinessOnCreate() {
        CreateEmployeeRequest request = buildCreateRequest(Set.of(4L));

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.existsByPhoneAndBusinessId("0981000000", 1L)).thenReturn(false);
        when(serviceRepository.findAllByIdInAndBusinessIdAndActiveTrue(Set.of(4L), 1L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void employeeResponseIncludesServices() {
        CreateEmployeeRequest request = buildCreateRequest(Set.of(4L));

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(employeeRepository.existsByPhoneAndBusinessId("0981000000", 1L)).thenReturn(false);
        when(serviceRepository.findAllByIdInAndBusinessIdAndActiveTrue(Set.of(4L), 1L))
                .thenReturn(List.of(serviceA));
        when(employeeMapper.toEntity(request)).thenReturn(employee);
        when(employeeRepository.save(employee)).thenReturn(employee);

        EmployeeServiceResponse serviceResponse = new EmployeeServiceResponse();
        serviceResponse.setId(4L);
        serviceResponse.setName("Corte");
        EmployeeResponse response = new EmployeeResponse();
        response.setServices(Set.of(serviceResponse));
        when(employeeMapper.toDto(employee)).thenReturn(response);

        EmployeeResponse result = employeeService.create(request);

        assertThat(result.getServices()).extracting(EmployeeServiceResponse::getName).containsExactly("Corte");
    }

    @Test
    void updatesEmployeeSuccessfully() {
        UpdateEmployeeRequest request = new UpdateEmployeeRequest();
        request.setFirstName("Juan Carlos");
        request.setLastName("Pérez");
        request.setPhone("0981000000");
        request.setColor("#000000");
        request.setServiceIds(Set.of(4L));

        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findAllByIdInAndBusinessIdAndActiveTrue(Set.of(4L), 1L))
                .thenReturn(List.of(serviceA));
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
    void updateReplacesPreviousServicesCompletely() {
        employee.setServices(new java.util.HashSet<>(Set.of(serviceB)));

        UpdateEmployeeRequest request = new UpdateEmployeeRequest();
        request.setFirstName("Juan");
        request.setLastName("Pérez");
        request.setPhone("0981000000");
        request.setColor("#3B82F6");
        request.setServiceIds(Set.of(4L));

        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findAllByIdInAndBusinessIdAndActiveTrue(Set.of(4L), 1L))
                .thenReturn(List.of(serviceA));
        when(employeeRepository.save(employee)).thenReturn(employee);
        when(employeeMapper.toDto(employee)).thenReturn(new EmployeeResponse());

        employeeService.update(5L, request);

        assertThat(employee.getServices()).containsExactly(serviceA);
        assertThat(employee.getServices()).doesNotContain(serviceB);
    }

    @Test
    void throwsResourceNotFoundWhenUpdatingWithServiceFromAnotherBusiness() {
        UpdateEmployeeRequest request = new UpdateEmployeeRequest();
        request.setFirstName("Juan");
        request.setLastName("Pérez");
        request.setPhone("0981000000");
        request.setColor("#3B82F6");
        request.setServiceIds(Set.of(999L));

        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.of(employee));
        when(serviceRepository.findAllByIdInAndBusinessIdAndActiveTrue(Set.of(999L), 1L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> employeeService.update(5L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void throwsResourceNotFoundWhenUpdatingEmployeeOfAnotherBusiness() {
        UpdateEmployeeRequest request = new UpdateEmployeeRequest();
        request.setFirstName("Juan Carlos");
        request.setLastName("Pérez");
        request.setPhone("0981000000");
        request.setColor("#000000");
        request.setServiceIds(Set.of(4L));

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
    void findByIdReturnsEmployeeWithServices() {
        EmployeeServiceResponse serviceResponse = new EmployeeServiceResponse();
        serviceResponse.setId(4L);
        serviceResponse.setName("Corte");
        EmployeeResponse response = new EmployeeResponse();
        response.setId(5L);
        response.setServices(Set.of(serviceResponse));

        when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(5L, 1L)).thenReturn(Optional.of(employee));
        when(employeeMapper.toDto(employee)).thenReturn(response);

        EmployeeResponse result = employeeService.findById(5L);

        assertThat(result.getServices()).extracting(EmployeeServiceResponse::getName).containsExactly("Corte");
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

    @Test
    void checksPhoneUniquenessScopedToAuthenticatedBusinessOnly() {
        when(currentUserProvider.getCurrentBusinessId()).thenReturn(2L);
        CreateEmployeeRequest request = buildCreateRequest(Set.of(4L));

        Business otherBusiness = new Business();
        otherBusiness.setId(2L);
        when(businessRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(otherBusiness));
        when(employeeRepository.existsByPhoneAndBusinessId("0981000000", 2L)).thenReturn(false);
        when(serviceRepository.findAllByIdInAndBusinessIdAndActiveTrue(Set.of(4L), 2L))
                .thenReturn(List.of(serviceA));
        when(employeeMapper.toEntity(request)).thenReturn(employee);
        when(employeeRepository.save(employee)).thenReturn(employee);
        when(employeeMapper.toDto(employee)).thenReturn(new EmployeeResponse());

        employeeService.create(request);

        verify(employeeRepository).existsByPhoneAndBusinessId("0981000000", 2L);
        verify(employeeRepository, never()).existsByPhoneAndBusinessId("0981000000", 1L);
    }

}
