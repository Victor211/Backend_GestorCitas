package com.victor.appointmentmanager.api.modules.whatsapp.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppInboundMessage {

    private String from;
    private String id;
    private String timestamp;
    private String type;
    private WhatsAppTextPayload text;

}
