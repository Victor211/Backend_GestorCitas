package com.victor.appointmentmanager.api.modules.conversations.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationMessageResponse;
import com.victor.appointmentmanager.api.modules.conversations.entity.Conversation;
import com.victor.appointmentmanager.api.modules.conversations.entity.ConversationMessage;
import com.victor.appointmentmanager.api.modules.conversations.enums.ConversationMode;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageDirection;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageSenderType;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageType;
import com.victor.appointmentmanager.api.modules.conversations.exception.ConversationNotFoundException;
import com.victor.appointmentmanager.api.modules.conversations.exception.ConversationNotInHumanModeException;
import com.victor.appointmentmanager.api.modules.conversations.mapper.ConversationMapper;
import com.victor.appointmentmanager.api.modules.conversations.repository.ConversationRepository;
import com.victor.appointmentmanager.api.modules.conversations.service.ConversationMessageService;
import com.victor.appointmentmanager.api.modules.whatsapp.client.WhatsAppClient;
import com.victor.appointmentmanager.api.modules.whatsapp.dto.response.WhatsAppSendMessageResponse;
import com.victor.appointmentmanager.api.modules.whatsapp.exception.WhatsAppApiException;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MVP 3 - Fase 2: envío manual de mensajes. Cubre ownership/modo/validación/orden envío-antes-de-
 * persistir a nivel unitario; el flujo HTTP + JWT real se cubre en
 * {@code ConversationManualMessageControllerIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class ConversationManualMessageServiceImplTest {

    private static final Long BUSINESS_ID = 1L;
    private static final Long CONVERSATION_ID = 15L;
    private static final String SENDER_PHONE = "595981000000";
    private static final String PHONE_NUMBER_ID = "PHONE_ID_1";

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private ConversationMessageService conversationMessageService;

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private WhatsAppClient whatsAppClient;

    private ConversationManualMessageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConversationManualMessageServiceImpl(conversationRepository, businessRepository,
                conversationMessageService, conversationMapper, currentUserProvider, whatsAppClient);
        lenient().when(currentUserProvider.getCurrentBusinessId()).thenReturn(BUSINESS_ID);
    }

    private Conversation humanConversation(Long customerId) {
        Conversation conversation = new Conversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setBusinessId(BUSINESS_ID);
        conversation.setSenderPhone(SENDER_PHONE);
        conversation.setCustomerId(customerId);
        conversation.setMode(ConversationMode.HUMAN);
        return conversation;
    }

    private Business business() {
        Business business = new Business();
        business.setId(BUSINESS_ID);
        business.setWhatsappPhoneNumberId(PHONE_NUMBER_ID);
        return business;
    }

    private WhatsAppSendMessageResponse sendResponseWithId(String wamid) {
        WhatsAppSendMessageResponse.SentMessage sentMessage = new WhatsAppSendMessageResponse.SentMessage();
        sentMessage.setId(wamid);
        WhatsAppSendMessageResponse response = new WhatsAppSendMessageResponse();
        response.setMessages(List.of(sentMessage));
        return response;
    }

    private ConversationMessage savedMessage() {
        ConversationMessage message = new ConversationMessage();
        message.setId(500L);
        message.setConversationId(CONVERSATION_ID);
        message.setDirection(MessageDirection.OUTBOUND);
        message.setSenderType(MessageSenderType.HUMAN);
        message.setMessageType(MessageType.TEXT);
        return message;
    }

    // ------------------------------------------------------------------
    // Caso feliz
    // ------------------------------------------------------------------

    @Test
    void humanModeConversationCanSendManualMessage() {
        Conversation conversation = humanConversation(8L);
        when(conversationRepository.findByIdAndBusinessId(CONVERSATION_ID, BUSINESS_ID))
                .thenReturn(Optional.of(conversation));
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business()));
        when(whatsAppClient.sendTextMessage(PHONE_NUMBER_ID, SENDER_PHONE, "Hola, te habla Juan"))
                .thenReturn(sendResponseWithId("wamid.OUT1"));
        when(conversationMessageService.recordOutbound(BUSINESS_ID, SENDER_PHONE, "wamid.OUT1",
                "Hola, te habla Juan", MessageSenderType.HUMAN)).thenReturn(savedMessage());
        when(conversationMapper.toMessageResponse(any())).thenReturn(new ConversationMessageResponse());

        ConversationMessageResponse response = service.sendMessage(CONVERSATION_ID, "Hola, te habla Juan");

        assertThat(response).isNotNull();
        verify(whatsAppClient).sendTextMessage(PHONE_NUMBER_ID, SENDER_PHONE, "Hola, te habla Juan");
        verify(conversationMessageService).recordOutbound(BUSINESS_ID, SENDER_PHONE, "wamid.OUT1",
                "Hola, te habla Juan", MessageSenderType.HUMAN);
        // El modo no cambia por enviar un mensaje: sigue HUMAN.
        assertThat(conversation.getMode()).isEqualTo(ConversationMode.HUMAN);
    }

    @Test
    void trimsContentBeforeSendingAndPersisting() {
        Conversation conversation = humanConversation(null);
        when(conversationRepository.findByIdAndBusinessId(CONVERSATION_ID, BUSINESS_ID))
                .thenReturn(Optional.of(conversation));
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business()));
        when(whatsAppClient.sendTextMessage(PHONE_NUMBER_ID, SENDER_PHONE, "Hola"))
                .thenReturn(sendResponseWithId("wamid.OUT2"));
        when(conversationMessageService.recordOutbound(BUSINESS_ID, SENDER_PHONE, "wamid.OUT2", "Hola",
                MessageSenderType.HUMAN)).thenReturn(savedMessage());
        when(conversationMapper.toMessageResponse(any())).thenReturn(new ConversationMessageResponse());

        service.sendMessage(CONVERSATION_ID, "  Hola  ");

        verify(whatsAppClient).sendTextMessage(PHONE_NUMBER_ID, SENDER_PHONE, "Hola");
        verify(conversationMessageService).recordOutbound(BUSINESS_ID, SENDER_PHONE, "wamid.OUT2", "Hola",
                MessageSenderType.HUMAN);
    }

    /** MVP 3 - Fase 2, ítem 16: customerId null no debe impedir el envío; el destinatario es senderPhone. */
    @Test
    void worksWhenConversationHasNoCustomerYet() {
        Conversation conversation = humanConversation(null);
        when(conversationRepository.findByIdAndBusinessId(CONVERSATION_ID, BUSINESS_ID))
                .thenReturn(Optional.of(conversation));
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business()));
        when(whatsAppClient.sendTextMessage(eq(PHONE_NUMBER_ID), eq(SENDER_PHONE), anyString()))
                .thenReturn(sendResponseWithId("wamid.OUT3"));
        when(conversationMessageService.recordOutbound(eq(BUSINESS_ID), eq(SENDER_PHONE), anyString(), anyString(),
                eq(MessageSenderType.HUMAN))).thenReturn(savedMessage());
        when(conversationMapper.toMessageResponse(any())).thenReturn(new ConversationMessageResponse());

        assertThat(service.sendMessage(CONVERSATION_ID, "Hola")).isNotNull();
    }

    @Test
    void usesConversationSenderPhoneAsRecipientNeverAnExternalPhone() {
        Conversation conversation = humanConversation(8L);
        conversation.setSenderPhone("595987654321");
        when(conversationRepository.findByIdAndBusinessId(CONVERSATION_ID, BUSINESS_ID))
                .thenReturn(Optional.of(conversation));
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business()));
        when(whatsAppClient.sendTextMessage(eq(PHONE_NUMBER_ID), eq("595987654321"), anyString()))
                .thenReturn(sendResponseWithId("wamid.OUT4"));
        when(conversationMessageService.recordOutbound(eq(BUSINESS_ID), eq("595987654321"), anyString(), anyString(),
                eq(MessageSenderType.HUMAN))).thenReturn(savedMessage());
        when(conversationMapper.toMessageResponse(any())).thenReturn(new ConversationMessageResponse());

        service.sendMessage(CONVERSATION_ID, "Hola");

        // No existe ningún parámetro de teléfono en sendMessage(conversationId, content): el único
        // destinatario posible es el que ya trae la Conversation validada por ownership.
        verify(whatsAppClient).sendTextMessage(eq(PHONE_NUMBER_ID), eq("595987654321"), anyString());
        verify(whatsAppClient, never()).sendTextMessage(eq(PHONE_NUMBER_ID), eq(SENDER_PHONE), anyString());
    }

    // ------------------------------------------------------------------
    // Modo BOT rechazado
    // ------------------------------------------------------------------

    @Test
    void botModeConversationCannotSendManualMessage() {
        Conversation conversation = humanConversation(8L);
        conversation.setMode(ConversationMode.BOT);
        when(conversationRepository.findByIdAndBusinessId(CONVERSATION_ID, BUSINESS_ID))
                .thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.sendMessage(CONVERSATION_ID, "Hola"))
                .isInstanceOf(ConversationNotInHumanModeException.class)
                .hasMessageContaining("HUMAN");

        verify(whatsAppClient, never()).sendTextMessage(any(), any(), any());
        verify(conversationMessageService, never()).recordOutbound(any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Ownership / multiempresa
    // ------------------------------------------------------------------

    @Test
    void anotherBusinessConversationIsNotFound() {
        when(conversationRepository.findByIdAndBusinessId(CONVERSATION_ID, BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage(CONVERSATION_ID, "Hola"))
                .isInstanceOf(ConversationNotFoundException.class);

        verify(whatsAppClient, never()).sendTextMessage(any(), any(), any());
    }

    @Test
    void nonexistentConversationIsNotFound() {
        when(conversationRepository.findByIdAndBusinessId(999L, BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage(999L, "Hola"))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // Falla de WhatsApp: no persiste
    // ------------------------------------------------------------------

    @Test
    void whatsAppFailureDoesNotPersistOutbound() {
        Conversation conversation = humanConversation(8L);
        when(conversationRepository.findByIdAndBusinessId(CONVERSATION_ID, BUSINESS_ID))
                .thenReturn(Optional.of(conversation));
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business()));
        when(whatsAppClient.sendTextMessage(eq(PHONE_NUMBER_ID), eq(SENDER_PHONE), anyString()))
                .thenThrow(new WhatsAppApiException("No se pudo enviar el mensaje mediante WhatsApp Cloud API"));

        assertThatThrownBy(() -> service.sendMessage(CONVERSATION_ID, "Hola"))
                .isInstanceOf(WhatsAppApiException.class);

        verify(conversationMessageService, never()).recordOutbound(any(), any(), any(), any(), any());
    }

    @Test
    void businessWithoutWhatsAppConfigurationIsRejectedBeforeAttemptingToSend() {
        Conversation conversation = humanConversation(8L);
        when(conversationRepository.findByIdAndBusinessId(CONVERSATION_ID, BUSINESS_ID))
                .thenReturn(Optional.of(conversation));
        Business businessWithoutPhoneNumberId = new Business();
        businessWithoutPhoneNumberId.setId(BUSINESS_ID);
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(businessWithoutPhoneNumberId));

        assertThatThrownBy(() -> service.sendMessage(CONVERSATION_ID, "Hola"))
                .isInstanceOf(BusinessException.class);

        verify(whatsAppClient, never()).sendTextMessage(any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Envía exactamente una vez
    // ------------------------------------------------------------------

    @Test
    void callsWhatsAppExactlyOnce() {
        Conversation conversation = humanConversation(8L);
        when(conversationRepository.findByIdAndBusinessId(CONVERSATION_ID, BUSINESS_ID))
                .thenReturn(Optional.of(conversation));
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business()));
        when(whatsAppClient.sendTextMessage(eq(PHONE_NUMBER_ID), eq(SENDER_PHONE), anyString()))
                .thenReturn(sendResponseWithId("wamid.OUT5"));
        when(conversationMessageService.recordOutbound(eq(BUSINESS_ID), eq(SENDER_PHONE), anyString(), anyString(),
                eq(MessageSenderType.HUMAN))).thenReturn(savedMessage());
        when(conversationMapper.toMessageResponse(any())).thenReturn(new ConversationMessageResponse());

        service.sendMessage(CONVERSATION_ID, "Hola");

        verify(whatsAppClient, org.mockito.Mockito.times(1)).sendTextMessage(any(), any(), any());
    }

    @Test
    void missingExternalMessageIdStillPersistsWithNullId() {
        Conversation conversation = humanConversation(8L);
        when(conversationRepository.findByIdAndBusinessId(CONVERSATION_ID, BUSINESS_ID))
                .thenReturn(Optional.of(conversation));
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business()));
        when(whatsAppClient.sendTextMessage(eq(PHONE_NUMBER_ID), eq(SENDER_PHONE), anyString()))
                .thenReturn(new WhatsAppSendMessageResponse());
        when(conversationMessageService.recordOutbound(eq(BUSINESS_ID), eq(SENDER_PHONE), isNull(), anyString(),
                eq(MessageSenderType.HUMAN))).thenReturn(savedMessage());
        when(conversationMapper.toMessageResponse(any())).thenReturn(new ConversationMessageResponse());

        service.sendMessage(CONVERSATION_ID, "Hola");

        verify(conversationMessageService).recordOutbound(eq(BUSINESS_ID), eq(SENDER_PHONE), isNull(), anyString(),
                eq(MessageSenderType.HUMAN));
    }

}
