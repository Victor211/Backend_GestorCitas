package com.victor.appointmentmanager.api.modules.customers.service;

import com.victor.appointmentmanager.api.modules.customers.dto.request.CreateCustomerRequest;
import com.victor.appointmentmanager.api.modules.customers.dto.request.UpdateCustomerRequest;
import com.victor.appointmentmanager.api.modules.customers.dto.response.CustomerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerService {

    CustomerResponse create(CreateCustomerRequest request);

    CustomerResponse update(Long id, UpdateCustomerRequest request);

    CustomerResponse findById(Long id);

    Page<CustomerResponse> findAll(String name, Pageable pageable);

    CustomerResponse findByPhone(String phone);

    void delete(Long id);

}
