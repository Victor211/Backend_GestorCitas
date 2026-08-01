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
import com.victor.appointmentmanager.api.security.BusinessAccessValidator;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final BusinessRepository businessRepository;
    private final EmployeeMapper employeeMapper;
    private final BusinessAccessValidator businessAccessValidator;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        businessAccessValidator.validate(request.getBusinessId());
        assertPhoneIsAvailable(request.getPhone());
        Business business = findActiveBusinessOrThrow(request.getBusinessId());

        Employee employee = employeeMapper.toEntity(request);
        employee.setBusiness(business);

        Employee saved = employeeRepository.save(employee);
        return employeeMapper.toDto(saved);
    }

    @Override
    @Transactional
    public EmployeeResponse update(Long id, UpdateEmployeeRequest request) {
        businessAccessValidator.validate(request.getBusinessId());
        Employee employee = findOwnedByIdOrThrow(id);

        if (!employee.getPhone().equals(request.getPhone())) {
            assertPhoneIsAvailable(request.getPhone());
        }

        Business business = findActiveBusinessOrThrow(request.getBusinessId());

        employeeMapper.updateEntityFromRequest(request, employee);
        employee.setBusiness(business);

        Employee updated = employeeRepository.save(employee);
        return employeeMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse findById(Long id) {
        return employeeMapper.toDto(findOwnedByIdOrThrow(id));
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
        Employee employee = findOwnedByIdOrThrow(id);
        employee.setActive(false);
        employeeRepository.save(employee);
    }

    private Employee findOwnedByIdOrThrow(Long id) {
        return employeeRepository.findByIdAndBusinessIdAndActiveTrue(id, currentUserProvider.getCurrentBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado con id " + id));
    }

    private Business findActiveBusinessOrThrow(Long businessId) {
        return businessRepository.findByIdAndActiveTrue(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con id " + businessId));
    }

    private void assertPhoneIsAvailable(String phone) {
        if (employeeRepository.existsByPhone(phone)) {
            throw new BusinessException("Ya existe un empleado con el teléfono '" + phone + "'");
        }
    }

}
