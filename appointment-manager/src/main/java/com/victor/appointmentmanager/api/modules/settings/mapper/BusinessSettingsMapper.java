package com.victor.appointmentmanager.api.modules.settings.mapper;

import com.victor.appointmentmanager.api.modules.settings.dto.request.UpdateBusinessSettingsRequest;
import com.victor.appointmentmanager.api.modules.settings.dto.response.BusinessSettingsResponse;
import com.victor.appointmentmanager.api.shared.entity.Business;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.util.StringUtils;

@Mapper
public interface BusinessSettingsMapper {

    @Mapping(target = "whatsappConfigured", ignore = true)
    BusinessSettingsResponse toResponse(Business business);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "timezone", ignore = true)
    @Mapping(target = "whatsappBusinessAccountId", ignore = true)
    @Mapping(target = "whatsappPhoneNumberId", ignore = true)
    void updateEntityFromRequest(UpdateBusinessSettingsRequest request, @MappingTarget Business business);

    @AfterMapping
    default void mapWhatsappConfigured(Business business, @MappingTarget BusinessSettingsResponse response) {
        response.setWhatsappConfigured(StringUtils.hasText(business.getWhatsappPhoneNumberId()));
    }

}
