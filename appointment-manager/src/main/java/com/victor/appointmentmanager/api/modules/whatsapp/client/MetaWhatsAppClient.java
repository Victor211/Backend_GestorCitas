package com.victor.appointmentmanager.api.modules.whatsapp.client;

import com.victor.appointmentmanager.api.modules.whatsapp.dto.request.WhatsAppTextContent;
import com.victor.appointmentmanager.api.modules.whatsapp.dto.request.WhatsAppTextMessageRequest;
import com.victor.appointmentmanager.api.modules.whatsapp.dto.response.WhatsAppSendMessageResponse;
import com.victor.appointmentmanager.api.modules.whatsapp.exception.WhatsAppApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Toda la comunicación con WhatsApp Cloud API queda encapsulada en esta clase.
 *
 * <p>El RestClient.Builder se recibe inyectado (bean auto-configurado por Spring Boot) en vez de
 * construirse aquí: eso permite fijar los timeouts de forma centralizada vía
 * spring.http.client.connect-timeout / read-timeout (ver application.yml), y permite testear esta
 * clase con MockRestServiceServer sin pegarle a Meta.
 */
@Component
public class MetaWhatsAppClient implements WhatsAppClient {

    private final RestClient restClient;
    private final String graphApiVersion;

    public MetaWhatsAppClient(RestClient.Builder restClientBuilder,
                               @Value("${app.whatsapp.access-token}") String accessToken,
                               @Value("${app.whatsapp.base-url}") String baseUrl,
                               @Value("${app.whatsapp.graph-api-version}") String graphApiVersion) {
        this.graphApiVersion = graphApiVersion;

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();
    }

    @Override
    public WhatsAppSendMessageResponse sendTextMessage(String phoneNumberId, String recipientPhone, String message) {
        WhatsAppTextMessageRequest request = new WhatsAppTextMessageRequest(recipientPhone,
                new WhatsAppTextContent(message));

        try {
            return restClient.post()
                    .uri("/{version}/{phoneNumberId}/messages", graphApiVersion, phoneNumberId)
                    .body(request)
                    .retrieve()
                    .body(WhatsAppSendMessageResponse.class);
        } catch (RestClientException ex) {
            throw new WhatsAppApiException("No se pudo enviar el mensaje mediante WhatsApp Cloud API");
        }
    }

}
