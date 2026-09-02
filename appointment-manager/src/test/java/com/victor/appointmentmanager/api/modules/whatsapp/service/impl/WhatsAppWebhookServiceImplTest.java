package com.victor.appointmentmanager.api.modules.whatsapp.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victor.appointmentmanager.api.modules.ai.dto.response.ConversationResponse;
import com.victor.appointmentmanager.api.modules.ai.enums.ConversationIntent;
import com.victor.appointmentmanager.api.modules.ai.service.ConversationService;
import com.victor.appointmentmanager.api.modules.conversations.service.ConversationMessageService;
import com.victor.appointmentmanager.api.modules.whatsapp.client.WhatsAppClient;
import com.victor.appointmentmanager.api.modules.whatsapp.config.WhatsAppSignatureValidator;
import com.victor.appointmentmanager.api.modules.whatsapp.dto.response.WhatsAppSendMessageResponse;
import com.victor.appointmentmanager.api.modules.whatsapp.entity.WhatsAppInboundEvent;
import com.victor.appointmentmanager.api.modules.whatsapp.exception.WhatsAppApiException;
import com.victor.appointmentmanager.api.modules.whatsapp.repository.WhatsAppInboundEventRepository;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
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
    private ConversationMessageService conversationMessageService;

    @Mock
    private WhatsAppClient whatsAppClient;

    private WhatsAppWebhookServiceImpl webhookService;

    private Business business;

    @BeforeEach
    void setUp() {
        webhookService = new WhatsAppWebhookServiceImpl(
                new ObjectMapper(), signatureValidator, businessRepository, inboundEventRepository,
                eventTracker, conversationService, conversationMessageService, whatsAppClient,
                "expected-verify-token");

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
        when(conversationService.processChannelConversation(1L, "595981000000", "Hola"))
                .thenReturn(conversationResponse);

        WhatsAppSendMessageResponse.SentMessage sentMessage = new WhatsAppSendMessageResponse.SentMessage();
        sentMessage.setId("wamid.OUT1");
        WhatsAppSendMessageResponse sendResponse = new WhatsAppSendMessageResponse();
        sendResponse.setMessages(List.of(sentMessage));
        when(whatsAppClient.sendTextMessage("PHONE_ID_1", "595981000000", "¡Hola! ¿En qué puedo ayudarte?"))
                .thenReturn(sendResponse);

        webhookService.processWebhook(textMessagePayload("wamid.MSG1", "595981000000", "Hola"));

        verify(conversationService).processChannelConversation(1L, "595981000000", "Hola");
        verify(whatsAppClient).sendTextMessage("PHONE_ID_1", "595981000000", "¡Hola! ¿En qué puedo ayudarte?");
        verify(eventTracker).markProcessed(100L);
        verify(eventTracker, never()).markFailed(any(), anyString());

        // INBOUND se guarda con el texto exacto ANTES de invocar al bot; OUTBOUND se guarda DESPUÉS
        // de que Cloud API aceptó el envío, con el id real que devolvió Meta.
        InOrder order = inOrder(conversationMessageService, conversationService, whatsAppClient);
        order.verify(conversationMessageService).recordInbound(1L, "595981000000", "wamid.MSG1", "Hola");
        order.verify(conversationService).processChannelConversation(1L, "595981000000", "Hola");
        order.verify(whatsAppClient).sendTextMessage("PHONE_ID_1", "595981000000", "¡Hola! ¿En qué puedo ayudarte?");
        order.verify(conversationMessageService).recordOutbound(1L, "595981000000", "wamid.OUT1",
                "¡Hola! ¿En qué puedo ayudarte?");
    }

    @Test
    void ignoresMessageWhenBusinessNotFound() {
        when(inboundEventRepository.findByExternalMessageId("wamid.MSG2")).thenReturn(Optional.empty());
        when(businessRepository.findByWhatsappPhoneNumberIdAndActiveTrue("PHONE_ID_1"))
                .thenReturn(Optional.empty());

        webhookService.processWebhook(textMessagePayload("wamid.MSG2", "595981000000", "Hola"));

        verify(eventTracker, never()).registerReceived(any(), any(), any(), any());
        verify(conversationService, never()).processChannelConversation(anyLong(), anyString(), anyString());
    }

    @Test
    void ignoresDuplicateMessage() {
        when(inboundEventRepository.findByExternalMessageId("wamid.MSG3"))
                .thenReturn(Optional.of(existingEvent(5L)));

        webhookService.processWebhook(textMessagePayload("wamid.MSG3", "595981000000", "Hola"));

        verify(businessRepository, never()).findByWhatsappPhoneNumberIdAndActiveTrue(any());
        verify(conversationService, never()).processChannelConversation(anyLong(), anyString(), anyString());
        verify(conversationMessageService, never()).recordInbound(any(), any(), any(), any());
        verify(conversationMessageService, never()).recordOutbound(any(), any(), any(), any());
    }

    @Test
    void payloadWithoutEntriesDoesNotThrow() {
        assertThatCode(() -> webhookService.processWebhook("{\"object\":\"whatsapp_business_account\"}"))
                .doesNotThrowAnyException();

        verify(conversationService, never()).processChannelConversation(anyLong(), anyString(), anyString());
    }

    @Test
    void payloadWithoutMessagesIsIgnoredSafely() {
        String payload = """
                {"object":"whatsapp_business_account","entry":[{"id":"WABA_ID","changes":[
                  {"field":"messages","value":{"metadata":{"phone_number_id":"PHONE_ID_1"}}}
                ]}]}
                """;

        assertThatCode(() -> webhookService.processWebhook(payload)).doesNotThrowAnyException();

        verify(conversationService, never()).processChannelConversation(anyLong(), anyString(), anyString());
    }

    @Test
    void statusUpdateEventIsIgnored() {
        assertThatCode(() -> webhookService.processWebhook(statusOnlyPayload())).doesNotThrowAnyException();

        verify(businessRepository, never()).findByWhatsappPhoneNumberIdAndActiveTrue(any());
        verify(conversationService, never()).processChannelConversation(anyLong(), anyString(), anyString());
    }

    @Test
    void nonTextMessageGetsUnsupportedReplyWithoutCallingConversationService() {
        when(inboundEventRepository.findByExternalMessageId("wamid.MSG4")).thenReturn(Optional.empty());
        when(businessRepository.findByWhatsappPhoneNumberIdAndActiveTrue("PHONE_ID_1"))
                .thenReturn(Optional.of(business));
        when(eventTracker.registerReceived(1L, "wamid.MSG4", "595981000000", "image"))
                .thenReturn(existingEvent(101L));

        webhookService.processWebhook(nonTextMessagePayload("wamid.MSG4", "595981000000"));

        verify(conversationService, never()).processChannelConversation(anyLong(), anyString(), anyString());
        verify(whatsAppClient).sendTextMessage(eq("PHONE_ID_1"), eq("595981000000"),
                eq("Por el momento solo puedo procesar mensajes de texto."));
        verify(eventTracker).markProcessed(101L);
        // El tipo no soportado (imagen) nunca se guarda como INBOUND: ConversationMessage.messageType
        // solo admite TEXT en este MVP. El aviso de "no puedo procesar" sí es un mensaje de texto
        // real que el cliente recibió, así que se guarda como OUTBOUND.
        verify(conversationMessageService, never()).recordInbound(any(), any(), any(), any());
        verify(conversationMessageService).recordOutbound(eq(1L), eq("595981000000"), isNull(),
                eq("Por el momento solo puedo procesar mensajes de texto."));
    }

    @Test
    void marksEventFailedWhenConversationServiceThrows() {
        when(inboundEventRepository.findByExternalMessageId("wamid.MSG5")).thenReturn(Optional.empty());
        when(businessRepository.findByWhatsappPhoneNumberIdAndActiveTrue("PHONE_ID_1"))
                .thenReturn(Optional.of(business));
        when(eventTracker.registerReceived(1L, "wamid.MSG5", "595981000000", "text"))
                .thenReturn(existingEvent(102L));
        when(conversationService.processChannelConversation(anyLong(), anyString(), anyString()))
                .thenThrow(new RuntimeException("fallo interno de IA"));

        webhookService.processWebhook(textMessagePayload("wamid.MSG5", "595981000000", "Hola"));

        verify(eventTracker).markFailed(eq(102L), anyString());
        verify(eventTracker, never()).markProcessed(any());
        verify(whatsAppClient, never()).sendTextMessage(any(), any(), any());
        // El INBOUND se guarda ANTES de invocar al bot, así que sobrevive aunque el bot falle.
        verify(conversationMessageService).recordInbound(1L, "595981000000", "wamid.MSG5", "Hola");
        // Nunca se llegó al punto de enviar una respuesta: no debe registrarse ningún OUTBOUND.
        verify(conversationMessageService, never()).recordOutbound(any(), any(), any(), any());
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
        when(conversationService.processChannelConversation(anyLong(), anyString(), anyString()))
                .thenReturn(conversationResponse);
        when(whatsAppClient.sendTextMessage(any(), any(), any()))
                .thenThrow(new WhatsAppApiException("No se pudo enviar el mensaje"));

        webhookService.processWebhook(textMessagePayload("wamid.MSG6", "595981000000", "Hola"));

        verify(eventTracker).markFailed(eq(103L), anyString());
        verify(eventTracker, never()).markProcessed(any());
        // El envío nunca llegó a completarse (Cloud API rechazó el mensaje): no hay nada que
        // guardar como OUTBOUND.
        verify(conversationMessageService, never()).recordOutbound(any(), any(), any(), any());
    }

    @Test
    void historyPersistenceFailureDoesNotBlockBotReply() {
        // Requisito explícito del MVP 2 (Fase 1): una falla al persistir el historial nunca debe
        // impedir que el bot responda al cliente ni afectar el resultado del procesamiento.
        when(inboundEventRepository.findByExternalMessageId("wamid.MSG7")).thenReturn(Optional.empty());
        when(businessRepository.findByWhatsappPhoneNumberIdAndActiveTrue("PHONE_ID_1"))
                .thenReturn(Optional.of(business));
        when(eventTracker.registerReceived(1L, "wamid.MSG7", "595981000000", "text"))
                .thenReturn(existingEvent(104L));

        ConversationResponse conversationResponse = new ConversationResponse(
                "Respuesta", ConversationIntent.UNKNOWN, 0.1, null);
        when(conversationService.processChannelConversation(anyLong(), anyString(), anyString()))
                .thenReturn(conversationResponse);
        org.mockito.Mockito.doThrow(new RuntimeException("fallo de base de datos"))
                .when(conversationMessageService).recordInbound(any(), any(), any(), any());
        org.mockito.Mockito.doThrow(new RuntimeException("fallo de base de datos"))
                .when(conversationMessageService).recordOutbound(any(), any(), any(), any());

        webhookService.processWebhook(textMessagePayload("wamid.MSG7", "595981000000", "Hola"));

        verify(whatsAppClient).sendTextMessage("PHONE_ID_1", "595981000000", "Respuesta");
        verify(eventTracker).markProcessed(104L);
        verify(eventTracker, never()).markFailed(any(), anyString());
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
