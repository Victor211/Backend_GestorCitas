package com.victor.appointmentmanager.api.modules.settings.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BusinessSettingsResponse {

    private Long id;
    private String name;
    private String phone;
    private String email;
    private String address;
    private String timezone;
    private boolean whatsappConfigured;

}
