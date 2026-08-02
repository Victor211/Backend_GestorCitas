package com.victor.appointmentmanager.api.modules.services.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;
import com.victor.appointmentmanager.api.modules.services.dto.request.CreateServiceRequest;
import com.victor.appointmentmanager.api.modules.services.dto.request.UpdateServiceRequest;
import com.victor.appointmentmanager.api.modules.services.dto.response.ServiceResponse;
import com.victor.appointmentmanager.api.modules.services.entity.Service;
import com.victor.appointmentmanager.api.modules.services.mapper.ServiceMapper;
import com.victor.appointmentmanager.api.modules.services.repository.ServiceRepository;
import com.victor.appointmentmanager.api.modules.services.service.ServiceService;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ServiceServiceImpl implements ServiceService {

    private final ServiceRepository serviceRepository;
    private final BusinessRepository businessRepository;
    private final ServiceMapper serviceMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public ServiceResponse create(CreateServiceRequest request) {
        Long businessId = currentUserProvider.getCurrentBusinessId();
        Business business = findActiveBusinessOrThrow(businessId);
        assertNameIsAvailable(request.getName(), businessId);

        Service service = serviceMapper.toEntity(request);
        service.setBusiness(business);

        Service saved = serviceRepository.save(service);
        return serviceMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ServiceResponse update(Long id, UpdateServiceRequest request) {
        Long businessId = currentUserProvider.getCurrentBusinessId();
        Service service = findOwnedByIdOrThrow(id, businessId);

        if (!service.getName().equalsIgnoreCase(request.getName())) {
            assertNameIsAvailable(request.getName(), businessId);
        }

        serviceMapper.updateEntityFromRequest(request, service);

        Service updated = serviceRepository.save(service);
        return serviceMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceResponse findById(Long id) {
        return serviceMapper.toDto(findOwnedByIdOrThrow(id, currentUserProvider.getCurrentBusinessId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ServiceResponse> findAll(String name, Pageable pageable) {
        String searchTerm = name != null ? name : "";
        return serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        currentUserProvider.getCurrentBusinessId(), searchTerm, pageable)
                .map(serviceMapper::toDto);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Service service = findOwnedByIdOrThrow(id, currentUserProvider.getCurrentBusinessId());
        service.setActive(false);
        serviceRepository.save(service);
    }

    private Service findOwnedByIdOrThrow(Long id, Long businessId) {
        return serviceRepository.findByIdAndBusinessIdAndActiveTrue(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con id " + id));
    }

    private Business findActiveBusinessOrThrow(Long businessId) {
        return businessRepository.findByIdAndActiveTrue(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con id " + businessId));
    }

    private void assertNameIsAvailable(String name, Long businessId) {
        if (serviceRepository.existsByNameIgnoreCaseAndBusinessId(name, businessId)) {
            throw new BusinessException("Ya existe un servicio con el nombre '" + name + "'");
        }
    }

}
