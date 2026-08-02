package com.victor.appointmentmanager.api.modules.whatsapp.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppWebhookValue {

    @JsonProperty("messaging_product")
    private String messagingProduct;

    private WhatsAppMetadata metadata;
    private List<WhatsAppContact> contacts;
    private List<WhatsAppInboundMessage> messages;
    private List<WhatsAppMessageStatus> statuses;

}
