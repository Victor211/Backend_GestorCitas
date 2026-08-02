package com.victor.appointmentmanager.api.modules.whatsapp.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WhatsAppTextMessageRequest {

    @JsonProperty("messaging_product")
    private final String messagingProduct = "whatsapp";

    private String to;

    private final String type = "text";

    private WhatsAppTextContent text;

    public WhatsAppTextMessageRequest(String to, WhatsAppTextContent text) {
        this.to = to;
        this.text = text;
    }

}
