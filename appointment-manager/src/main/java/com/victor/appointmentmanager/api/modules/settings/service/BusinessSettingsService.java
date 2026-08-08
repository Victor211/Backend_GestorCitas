package com.victor.appointmentmanager.api.modules.settings.service;

import com.victor.appointmentmanager.api.modules.settings.dto.request.UpdateBusinessSettingsRequest;
import com.victor.appointmentmanager.api.modules.settings.dto.response.BusinessSettingsResponse;

public interface BusinessSettingsService {

    BusinessSettingsResponse getSettings();

    BusinessSettingsResponse updateSettings(UpdateBusinessSettingsRequest request);

}
