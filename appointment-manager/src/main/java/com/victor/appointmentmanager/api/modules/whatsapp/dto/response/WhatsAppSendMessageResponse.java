package com.victor.appointmentmanager.api.modules.whatsapp.dto.response;

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
public class WhatsAppSendMessageResponse {

    @JsonProperty("messaging_product")
    private String messagingProduct;

    private List<SentMessage> messages;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SentMessage {
        private String id;
    }

}
