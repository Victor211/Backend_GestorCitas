package com.victor.appointmentmanager.api.modules.customers.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;
import com.victor.appointmentmanager.api.modules.customers.dto.request.CreateCustomerRequest;
import com.victor.appointmentmanager.api.modules.customers.dto.request.UpdateCustomerRequest;
import com.victor.appointmentmanager.api.modules.customers.dto.response.CustomerResponse;
import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import com.victor.appointmentmanager.api.modules.customers.exception.CustomerNotFoundException;
import com.victor.appointmentmanager.api.modules.customers.mapper.CustomerMapper;
import com.victor.appointmentmanager.api.modules.customers.repository.CustomerRepository;
import com.victor.appointmentmanager.api.modules.customers.service.CustomerService;
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
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final BusinessRepository businessRepository;
    private final CustomerMapper customerMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        Long businessId = currentUserProvider.getCurrentBusinessId();
        Business business = findActiveBusinessOrThrow(businessId);
        assertPhoneIsAvailable(businessId, request.getPhone());

        Customer customer = customerMapper.toEntity(request);
        customer.setBusiness(business);

        Customer saved = customerRepository.save(customer);
        return customerMapper.toDto(saved);
    }

    @Override
    @Transactional
    public CustomerResponse update(Long id, UpdateCustomerRequest request) {
        Customer customer = findOwnedByIdOrThrow(id);

        if (!customer.getPhone().equals(request.getPhone())) {
            assertPhoneIsAvailable(customer.getBusiness().getId(), request.getPhone());
        }

        customerMapper.updateEntityFromRequest(request, customer);

        Customer updated = customerRepository.save(customer);
        return customerMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse findById(Long id) {
        return customerMapper.toDto(findOwnedByIdOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> findAll(String name, Pageable pageable) {
        Long businessId = currentUserProvider.getCurrentBusinessId();
        String searchTerm = name != null ? name : "";
        return customerRepository.searchActiveByBusinessAndName(businessId, searchTerm, pageable)
                .map(customerMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse findByPhone(String phone) {
        Long businessId = currentUserProvider.getCurrentBusinessId();

        Customer customer = customerRepository.findByBusinessIdAndPhoneAndActiveTrue(businessId, phone)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Cliente no encontrado con teléfono '" + phone + "' en el negocio " + businessId));

        return customerMapper.toDto(customer);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Customer customer = findOwnedByIdOrThrow(id);
        customer.setActive(false);
        customerRepository.save(customer);
    }

    private Customer findOwnedByIdOrThrow(Long id) {
        return customerRepository.findByIdAndBusinessIdAndActiveTrue(id, currentUserProvider.getCurrentBusinessId())
                .orElseThrow(() -> new CustomerNotFoundException("Cliente no encontrado con id " + id));
    }

    private Business findActiveBusinessOrThrow(Long businessId) {
        return businessRepository.findByIdAndActiveTrue(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con id " + businessId));
    }

    private void assertPhoneIsAvailable(Long businessId, String phone) {
        if (customerRepository.existsByBusinessIdAndPhone(businessId, phone)) {
            throw new BusinessException("Ya existe un cliente con el teléfono '" + phone + "' en este negocio");
        }
    }

}
