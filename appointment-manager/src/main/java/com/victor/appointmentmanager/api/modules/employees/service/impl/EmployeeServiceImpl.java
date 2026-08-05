package com.victor.appointmentmanager.api.modules.employees.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;
import com.victor.appointmentmanager.api.modules.employees.dto.request.CreateEmployeeRequest;
import com.victor.appointmentmanager.api.modules.employees.dto.request.UpdateEmployeeRequest;
import com.victor.appointmentmanager.api.modules.employees.dto.response.EmployeeResponse;
import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import com.victor.appointmentmanager.api.modules.employees.mapper.EmployeeMapper;
import com.victor.appointmentmanager.api.modules.employees.repository.EmployeeRepository;
import com.victor.appointmentmanager.api.modules.employees.service.EmployeeService;
import com.victor.appointmentmanager.api.modules.services.entity.Service;
import com.victor.appointmentmanager.api.modules.services.repository.ServiceRepository;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final BusinessRepository businessRepository;
    private final ServiceRepository serviceRepository;
    private final EmployeeMapper employeeMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        Long businessId = currentUserProvider.getCurrentBusinessId();
        Business business = findActiveBusinessOrThrow(businessId);
        assertPhoneIsAvailable(request.getPhone(), businessId);
        Set<Service> services = findOwnedServicesOrThrow(request.getServiceIds(), businessId);

        Employee employee = employeeMapper.toEntity(request);
        employee.setBusiness(business);
        employee.setServices(services);

        Employee saved = employeeRepository.save(employee);
        return employeeMapper.toDto(saved);
    }

    @Override
    @Transactional
    public EmployeeResponse update(Long id, UpdateEmployeeRequest request) {
        Long businessId = currentUserProvider.getCurrentBusinessId();
        Employee employee = findOwnedByIdOrThrow(id, businessId);

        if (!Objects.equals(employee.getPhone(), request.getPhone())) {
            assertPhoneIsAvailable(request.getPhone(), businessId);
        }

        Set<Service> services = findOwnedServicesOrThrow(request.getServiceIds(), businessId);

        employeeMapper.updateEntityFromRequest(request, employee);
        employee.setServices(services);

        Employee updated = employeeRepository.save(employee);
        return employeeMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse findById(Long id) {
        return employeeMapper.toDto(findOwnedByIdOrThrow(id, currentUserProvider.getCurrentBusinessId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeResponse> findAll(String firstName, Pageable pageable) {
        String searchTerm = firstName != null ? firstName : "";
        return employeeRepository.findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(
                        currentUserProvider.getCurrentBusinessId(), searchTerm, pageable)
                .map(employeeMapper::toDto);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Employee employee = findOwnedByIdOrThrow(id, currentUserProvider.getCurrentBusinessId());
        employee.setActive(false);
        employeeRepository.save(employee);
    }

    private Employee findOwnedByIdOrThrow(Long id, Long businessId) {
        return employeeRepository.findByIdAndBusinessIdAndActiveTrue(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con id " + id));
    }

    private Business findActiveBusinessOrThrow(Long businessId) {
        return businessRepository.findByIdAndActiveTrue(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con id " + businessId));
    }

    private void assertPhoneIsAvailable(String phone, Long businessId) {
        if (phone == null) {
            return;
        }
        if (employeeRepository.existsByPhoneAndBusinessId(phone, businessId)) {
            throw new BusinessException("Ya existe un empleado con el teléfono '" + phone + "'");
        }
    }

    private Set<Service> findOwnedServicesOrThrow(Set<Long> serviceIds, Long businessId) {
        List<Service> services = serviceRepository.findAllByIdInAndBusinessIdAndActiveTrue(serviceIds, businessId);
        if (services.size() != serviceIds.size()) {
            throw new ResourceNotFoundException(
                    "Uno o más servicios no existen, no están activos o no pertenecen a este negocio");
        }
        return new HashSet<>(services);
    }

}
