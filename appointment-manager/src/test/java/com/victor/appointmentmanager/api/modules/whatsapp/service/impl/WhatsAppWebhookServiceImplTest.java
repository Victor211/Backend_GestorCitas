package com.victor.appointmentmanager.api.modules.whatsapp.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victor.appointmentmanager.api.modules.ai.dto.request.ConversationRequest;
import com.victor.appointmentmanager.api.modules.ai.dto.response.ConversationResponse;
import com.victor.appointmentmanager.api.modules.ai.enums.ConversationIntent;
import com.victor.appointmentmanager.api.modules.ai.service.ConversationService;
import com.victor.appointmentmanager.api.modules.whatsapp.client.WhatsAppClient;
import com.victor.appointmentmanager.api.modules.whatsapp.config.WhatsAppSignatureValidator;
import com.victor.appointmentmanager.api.modules.whatsapp.entity.WhatsAppInboundEvent;
import com.victor.appointmentmanager.api.modules.whatsapp.exception.WhatsAppApiException;
import com.victor.appointmentmanager.api.modules.whatsapp.repository.WhatsAppInboundEventRepository;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookServiceImplTest {

    @Mock
    private WhatsAppSignatureValidator signatureValidator;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private WhatsAppInboundEventRepository inboundEventRepository;

    @Mock
    private WhatsAppInboundEventTracker eventTracker;

    @Mock
    private ConversationService conversationService;

    @Mock
    private WhatsAppClient whatsAppClient;

    private WhatsAppWebhookServiceImpl webhookService;

    private Business business;

    @BeforeEach
    void setUp() {
        webhookService = new WhatsAppWebhookServiceImpl(
                new ObjectMapper(), signatureValidator, businessRepository, inboundEventRepository,
                eventTracker, conversationService, whatsAppClient, "expected-verify-token");

        business = new Business();
        business.setId(1L);
        business.setName("Barbería Central");
        business.setWhatsappPhoneNumberId("PHONE_ID_1");
    }

    private String textMessagePayload(String externalMessageId, String senderPhone, String messageBody) {
        return """
                {
                  "object": "whatsapp_business_account",
                  "entry": [
                    {
                      "id": "WABA_ID",
                      "changes": [
                        {
                          "field": "messages",
                          "value": {
                            "messaging_product": "whatsapp",
                            "metadata": {"display_phone_number": "15550001111", "phone_number_id": "PHONE_ID_1"},
                            "contacts": [{"profile": {"name": "Ana"}, "wa_id": "%s"}],
                            "messages": [
                              {"from": "%s", "id": "%s", "timestamp": "1690000000", "type": "text",
                               "text": {"body": "%s"}}
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(senderPhone, senderPhone, externalMessageId, messageBody);
    }

    private String nonTextMessagePayload(String externalMessageId, String senderPhone) {
        return """
                {
                  "object": "whatsapp_business_account",
                  "entry": [
                    {
                      "id": "WABA_ID",
                      "changes": [
                        {
                          "field": "messages",
                          "value": {
                            "metadata": {"phone_number_id": "PHONE_ID_1"},
                            "messages": [
                              {"from": "%s", "id": "%s", "timestamp": "1690000000", "type": "image"}
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(senderPhone, externalMessageId);
    }

    private String statusOnlyPayload() {
        return """
                {
                  "object": "whatsapp_business_account",
                  "entry": [
                    {
                      "id": "WABA_ID",
                      "changes": [
                        {
                          "field": "messages",
                          "value": {
                            "metadata": {"phone_number_id": "PHONE_ID_1"},
                            "statuses": [
                              {"id": "wamid.STATUS1", "status": "delivered", "timestamp": "1690000000",
                               "recipient_id": "595981000000"}
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    private WhatsAppInboundEvent existingEvent(Long id) {
        WhatsAppInboundEvent event = new WhatsAppInboundEvent();
        event.setId(id);
        return event;
    }

    @Test
    void processesTextMessageSuccessfullyAndMarksProcessed() {
        when(inboundEventRepository.findByExternalMessageId("wamid.MSG1")).thenReturn(Optional.empty());
        when(businessRepository.findByWhatsappPhoneNumberIdAndActiveTrue("PHONE_ID_1"))
                .thenReturn(Optional.of(business));
        when(eventTracker.registerReceived(1L, "wamid.MSG1", "595981000000", "text"))
                .thenReturn(existingEvent(100L));

        ConversationResponse conversationResponse = new ConversationResponse(
                "¡Hola! ¿En qué puedo ayudarte?", ConversationIntent.GREETING, 0.9, null);
        when(conversationService.handleMessage(any(ConversationRequest.class))).thenReturn(conversationResponse);

        webhookService.processWebhook(textMessagePayload("wamid.MSG1", "595981000000", "Hola"));

        ArgumentCaptor<ConversationRequest> requestCaptor = ArgumentCaptor.forClass(ConversationRequest.class);
        verify(conversationService).handleMessage(requestCaptor.capture());
        assertThatCode(() -> {
            ConversationRequest sent = requestCaptor.getValue();
            org.assertj.core.api.Assertions.assertThat(sent.getBusinessId()).isEqualTo(1L);
            org.assertj.core.api.Assertions.assertThat(sent.getCustomerPhone()).isEqualTo("595981000000");
            org.assertj.core.api.Assertions.assertThat(sent.getMessage()).isEqualTo("Hola");
        }).doesNotThrowAnyException();

        verify(whatsAppClient).sendTextMessage("PHONE_ID_1", "595981000000", "¡Hola! ¿En qué puedo ayudarte?");
        verify(eventTracker).markProcessed(100L);
        verify(eventTracker, never()).markFailed(any(), anyString());
    }

    @Test
    void ignoresMessageWhenBusinessNotFound() {
        when(inboundEventRepository.findByExternalMessageId("wamid.MSG2")).thenReturn(Optional.empty());
        when(businessRepository.findByWhatsappPhoneNumberIdAndActiveTrue("PHONE_ID_1"))
                .thenReturn(Optional.empty());

        webhookService.processWebhook(textMessagePayload("wamid.MSG2", "595981000000", "Hola"));

        verify(eventTracker, never()).registerReceived(any(), any(), any(), any());
        verify(conversationService, never()).handleMessage(any());
    }

    @Test
    void ignoresDuplicateMessage() {
        when(inboundEventRepository.findByExternalMessageId("wamid.MSG3"))
                .thenReturn(Optional.of(existingEvent(5L)));

        webhookService.processWebhook(textMessagePayload("wamid.MSG3", "595981000000", "Hola"));

        verify(businessRepository, never()).findByWhatsappPhoneNumberIdAndActiveTrue(any());
        verify(conversationService, never()).handleMessage(any());
    }

    @Test
    void payloadWithoutEntriesDoesNotThrow() {
        assertThatCode(() -> webhookService.processWebhook("{\"object\":\"whatsapp_business_account\"}"))
                .doesNotThrowAnyException();

        verify(conversationService, never()).handleMessage(any());
    }

    @Test
    void payloadWithoutMessagesIsIgnoredSafely() {
        String payload = """
                {"object":"whatsapp_business_account","entry":[{"id":"WABA_ID","changes":[
                  {"field":"messages","value":{"metadata":{"phone_number_id":"PHONE_ID_1"}}}
                ]}]}
                """;

        assertThatCode(() -> webhookService.processWebhook(payload)).doesNotThrowAnyException();

        verify(conversationService, never()).handleMessage(any());
    }

    @Test
    void statusUpdateEventIsIgnored() {
        assertThatCode(() -> webhookService.processWebhook(statusOnlyPayload())).doesNotThrowAnyException();

        verify(businessRepository, never()).findByWhatsappPhoneNumberIdAndActiveTrue(any());
        verify(conversationService, never()).handleMessage(any());
    }

    @Test
    void nonTextMessageGetsUnsupportedReplyWithoutCallingConversationService() {
        when(inboundEventRepository.findByExternalMessageId("wamid.MSG4")).thenReturn(Optional.empty());
        when(businessRepository.findByWhatsappPhoneNumberIdAndActiveTrue("PHONE_ID_1"))
                .thenReturn(Optional.of(business));
        when(eventTracker.registerReceived(1L, "wamid.MSG4", "595981000000", "image"))
                .thenReturn(existingEvent(101L));

        webhookService.processWebhook(nonTextMessagePayload("wamid.MSG4", "595981000000"));

        verify(conversationService, never()).handleMessage(any());
        verify(whatsAppClient).sendTextMessage(eq("PHONE_ID_1"), eq("595981000000"),
                eq("Por el momento solo puedo procesar mensajes de texto."));
        verify(eventTracker).markProcessed(101L);
    }

    @Test
    void marksEventFailedWhenConversationServiceThrows() {
        when(inboundEventRepository.findByExternalMessageId("wamid.MSG5")).thenReturn(Optional.empty());
        when(businessRepository.findByWhatsappPhoneNumberIdAndActiveTrue("PHONE_ID_1"))
                .thenReturn(Optional.of(business));
        when(eventTracker.registerReceived(1L, "wamid.MSG5", "595981000000", "text"))
                .thenReturn(existingEvent(102L));
        when(conversationService.handleMessage(any())).thenThrow(new RuntimeException("fallo interno de IA"));

        webhookService.processWebhook(textMessagePayload("wamid.MSG5", "595981000000", "Hola"));

        verify(eventTracker).markFailed(eq(102L), anyString());
        verify(eventTracker, never()).markProcessed(any());
        verify(whatsAppClient, never()).sendTextMessage(any(), any(), any());
    }

    @Test
    void marksEventFailedWhenSendingReplyFails() {
        when(inboundEventRepository.findByExternalMessageId("wamid.MSG6")).thenReturn(Optional.empty());
        when(businessRepository.findByWhatsappPhoneNumberIdAndActiveTrue("PHONE_ID_1"))
                .thenReturn(Optional.of(business));
        when(eventTracker.registerReceived(1L, "wamid.MSG6", "595981000000", "text"))
                .thenReturn(existingEvent(103L));

        ConversationResponse conversationResponse = new ConversationResponse(
                "Respuesta", ConversationIntent.UNKNOWN, 0.1, null);
        when(conversationService.handleMessage(any())).thenReturn(conversationResponse);
        when(whatsAppClient.sendTextMessage(any(), any(), any()))
                .thenThrow(new WhatsAppApiException("No se pudo enviar el mensaje"));

        webhookService.processWebhook(textMessagePayload("wamid.MSG6", "595981000000", "Hola"));

        verify(eventTracker).markFailed(eq(103L), anyString());
        verify(eventTracker, never()).markProcessed(any());
    }

    @Test
    void verifyWebhookAcceptsCorrectModeAndToken() {
        org.assertj.core.api.Assertions.assertThat(
                webhookService.verifyWebhook("subscribe", "expected-verify-token")).isTrue();
    }

    @Test
    void verifyWebhookRejectsIncorrectToken() {
        org.assertj.core.api.Assertions.assertThat(
                webhookService.verifyWebhook("subscribe", "wrong-token")).isFalse();
    }

}
