package com.victor.appointmentmanager.api.modules.services.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;
import com.victor.appointmentmanager.api.modules.services.dto.request.CreateServiceRequest;
import com.victor.appointmentmanager.api.modules.services.dto.request.UpdateServiceRequest;
import com.victor.appointmentmanager.api.modules.services.dto.response.ServiceResponse;
import com.victor.appointmentmanager.api.modules.services.entity.Service;
import com.victor.appointmentmanager.api.modules.services.mapper.ServiceMapper;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceServiceImplTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private ServiceMapper serviceMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private ServiceServiceImpl serviceService;

    private Business business;
    private Service service;

    @BeforeEach
    void setUp() {
        business = new Business();
        business.setId(1L);
        business.setName("Barbería Central");

        service = new Service();
        service.setId(10L);
        service.setName("Corte");
        service.setDurationMinutes(30);
        service.setPrice(new BigDecimal("15.00"));
        service.setColor("#3B82F6");
        service.setBusiness(business);
        service.setActive(true);

        lenient().when(currentUserProvider.getCurrentBusinessId()).thenReturn(1L);
    }

    private CreateServiceRequest buildCreateRequest() {
        CreateServiceRequest request = new CreateServiceRequest();
        request.setName("Corte");
        request.setDurationMinutes(30);
        request.setPrice(new BigDecimal("15.00"));
        request.setColor("#3B82F6");
        return request;
    }

    @Test
    void createsServiceUsingBusinessIdFromJwt() {
        CreateServiceRequest request = buildCreateRequest();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(serviceRepository.existsByNameIgnoreCaseAndBusinessId("Corte", 1L)).thenReturn(false);
        when(serviceMapper.toEntity(request)).thenReturn(service);
        when(serviceRepository.save(service)).thenReturn(service);

        ServiceResponse response = new ServiceResponse();
        response.setId(10L);
        when(serviceMapper.toDto(service)).thenReturn(response);

        ServiceResponse result = serviceService.create(request);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(service.getBusiness()).isEqualTo(business);
        verify(serviceRepository).save(service);
    }

    @Test
    void throwsBusinessExceptionWhenNameAlreadyExistsInBusinessOnCreate() {
        CreateServiceRequest request = buildCreateRequest();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(serviceRepository.existsByNameIgnoreCaseAndBusinessId("Corte", 1L)).thenReturn(true);

        assertThatThrownBy(() -> serviceService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(serviceRepository, never()).save(any());
    }

    @Test
    void updatesServiceSuccessfully() {
        UpdateServiceRequest request = new UpdateServiceRequest();
        request.setName("Corte Premium");
        request.setDurationMinutes(45);
        request.setPrice(new BigDecimal("20.00"));
        request.setColor("#000000");

        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(10L, 1L)).thenReturn(Optional.of(service));
        when(serviceRepository.existsByNameIgnoreCaseAndBusinessId("Corte Premium", 1L)).thenReturn(false);
        when(serviceRepository.save(service)).thenReturn(service);

        ServiceResponse response = new ServiceResponse();
        response.setName("Corte Premium");
        when(serviceMapper.toDto(service)).thenReturn(response);

        ServiceResponse result = serviceService.update(10L, request);

        assertThat(result.getName()).isEqualTo("Corte Premium");
        verify(serviceMapper).updateEntityFromRequest(request, service);
    }

    @Test
    void throwsResourceNotFoundWhenUpdatingServiceOfAnotherBusiness() {
        UpdateServiceRequest request = new UpdateServiceRequest();
        request.setName("Corte Premium");
        request.setDurationMinutes(45);
        request.setPrice(new BigDecimal("20.00"));
        request.setColor("#000000");

        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceService.update(10L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(serviceRepository, never()).save(any());
    }

    @Test
    void throwsResourceNotFoundWhenFindingServiceOfAnotherBusiness() {
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceService.findById(10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void softDeletesService() {
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(10L, 1L)).thenReturn(Optional.of(service));
        when(serviceRepository.save(service)).thenReturn(service);

        serviceService.delete(10L);

        assertThat(service.getActive()).isFalse();
        verify(serviceRepository).save(service);
    }

    @Test
    void throwsResourceNotFoundWhenDeletingServiceOfAnotherBusiness() {
        when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceService.delete(10L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(serviceRepository, never()).save(any());
    }

}
