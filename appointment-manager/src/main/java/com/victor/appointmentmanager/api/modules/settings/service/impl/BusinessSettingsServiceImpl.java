package com.victor.appointmentmanager.api.modules.settings.service.impl;

import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;
import com.victor.appointmentmanager.api.modules.settings.dto.request.UpdateBusinessSettingsRequest;
import com.victor.appointmentmanager.api.modules.settings.dto.response.BusinessSettingsResponse;
import com.victor.appointmentmanager.api.modules.settings.mapper.BusinessSettingsMapper;
import com.victor.appointmentmanager.api.modules.settings.service.BusinessSettingsService;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class BusinessSettingsServiceImpl implements BusinessSettingsService {

    private final BusinessRepository businessRepository;
    private final BusinessSettingsMapper businessSettingsMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public BusinessSettingsResponse getSettings() {
        Business business = findActiveBusinessOrThrow(currentUserProvider.getCurrentBusinessId());
        return businessSettingsMapper.toResponse(business);
    }

    @Override
    @Transactional
    public BusinessSettingsResponse updateSettings(UpdateBusinessSettingsRequest request) {
        Business business = findActiveBusinessOrThrow(currentUserProvider.getCurrentBusinessId());
        ZoneId zoneId = parseZoneIdOrThrow(request.getTimezone());

        businessSettingsMapper.updateEntityFromRequest(request, business);
        business.setTimezone(zoneId.getId());

        Business updated = businessRepository.save(business);
        return businessSettingsMapper.toResponse(updated);
    }

    private Business findActiveBusinessOrThrow(Long businessId) {
        return businessRepository.findByIdAndActiveTrue(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con id " + businessId));
    }

    private ZoneId parseZoneIdOrThrow(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La zona horaria indicada no es válida");
        }
    }

}
