package com.victor.appointmentmanager.api.modules.services.service;

import com.victor.appointmentmanager.api.modules.services.dto.request.CreateServiceRequest;
import com.victor.appointmentmanager.api.modules.services.dto.request.UpdateServiceRequest;
import com.victor.appointmentmanager.api.modules.services.dto.response.ServiceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ServiceService {

    ServiceResponse create(CreateServiceRequest request);

    ServiceResponse update(Long id, UpdateServiceRequest request);

    ServiceResponse findById(Long id);

    Page<ServiceResponse> findAll(String name, Pageable pageable);

    void delete(Long id);

}
