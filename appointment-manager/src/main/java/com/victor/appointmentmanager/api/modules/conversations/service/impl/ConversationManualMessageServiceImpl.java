package com.victor.appointmentmanager.api.modules.conversations.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationMessageResponse;
import com.victor.appointmentmanager.api.modules.conversations.entity.Conversation;
import com.victor.appointmentmanager.api.modules.conversations.entity.ConversationMessage;
import com.victor.appointmentmanager.api.modules.conversations.enums.ConversationMode;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageSenderType;
import com.victor.appointmentmanager.api.modules.conversations.exception.ConversationNotFoundException;
import com.victor.appointmentmanager.api.modules.conversations.exception.ConversationNotInHumanModeException;
import com.victor.appointmentmanager.api.modules.conversations.mapper.ConversationMapper;
import com.victor.appointmentmanager.api.modules.conversations.repository.ConversationRepository;
import com.victor.appointmentmanager.api.modules.conversations.service.ConversationManualMessageService;
import com.victor.appointmentmanager.api.modules.conversations.service.ConversationMessageService;
import com.victor.appointmentmanager.api.modules.whatsapp.client.WhatsAppClient;
import com.victor.appointmentmanager.api.modules.whatsapp.dto.response.WhatsAppSendMessageResponse;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationManualMessageServiceImpl implements ConversationManualMessageService {

    private static final String NOT_HUMAN_MODE_MESSAGE =
            "La conversación debe estar en modo HUMAN para enviar mensajes manualmente.";

    private final ConversationRepository conversationRepository;
    private final BusinessRepository businessRepository;
    private final ConversationMessageService conversationMessageService;
    private final ConversationMapper conversationMapper;
    private final CurrentUserProvider currentUserProvider;
    private final WhatsAppClient whatsAppClient;

    /**
     * Deliberadamente SIN {@code @Transactional} a nivel de método: la llamada de red a WhatsApp
     * Cloud API ({@link WhatsAppClient#sendTextMessage}) no debe quedar envuelta en una transacción
     * de base de datos abierta (mismo criterio que {@code WhatsAppWebhookServiceImpl}, que tampoco
     * es transaccional). Las únicas escrituras a la base ({@link ConversationMessageService#recordOutbound})
     * son, cada una, su propia transacción corta - ver Javadoc de esa clase. La lectura de
     * ownership/modo tampoco necesita transacción explícita: es un solo SELECT.
     */
    @Override
    public ConversationMessageResponse sendMessage(Long conversationId, String content) {
        Long businessId = currentUserProvider.getCurrentBusinessId();
        Conversation conversation = findOwnedByIdOrThrow(conversationId, businessId);

        // Punto de corte: solo se puede escribir manualmente si un operador ya tomó la
        // conversación. Nunca se cambia el modo automáticamente acá - el operador debe hacer
        // takeover explícito (endpoint separado, MVP 3 - Fase 1).
        if (conversation.getMode() != ConversationMode.HUMAN) {
            throw new ConversationNotInHumanModeException(NOT_HUMAN_MODE_MESSAGE);
        }

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado"));
        if (business.getWhatsappPhoneNumberId() == null) {
            throw new BusinessException("El negocio no tiene WhatsApp configurado.");
        }

        // Trim: mismo criterio de validación que @NotBlank, preservando el resto del texto tal
        // cual lo escribió el operador (sin normalizar ni convertir a HTML).
        String trimmedContent = content.trim();

        // Destinatario SIEMPRE desde la Conversation ya validada por ownership, nunca desde el
        // request: evita que un conversationId propio se use para mandar a un teléfono ajeno.
        WhatsAppSendMessageResponse response = whatsAppClient.sendTextMessage(
                business.getWhatsappPhoneNumberId(), conversation.getSenderPhone(), trimmedContent);

        // Solo se persiste si sendTextMessage no lanzó excepción (envía primero, persiste
        // después - ver Javadoc de la clase): un fallo de Cloud API nunca debe dejar un OUTBOUND
        // "fantasma" en el historial ni actualizar lastMessagePreview como si hubiera salido.
        ConversationMessage saved = conversationMessageService.recordOutbound(
                businessId, conversation.getSenderPhone(), extractOutboundMessageId(response), trimmedContent,
                MessageSenderType.HUMAN);

        log.info("Mensaje manual enviado. conversationId={}, businessId={}", conversation.getId(), businessId);
        return conversationMapper.toMessageResponse(saved);
    }

    private Conversation findOwnedByIdOrThrow(Long id, Long businessId) {
        return conversationRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ConversationNotFoundException("Conversación no encontrada con id " + id));
    }

    /**
     * Igual que {@code WhatsAppWebhookServiceImpl#extractOutboundMessageId}: si Cloud API no
     * devuelve el wamid (o el campo viene vacío), el OUTBOUND se persiste igual con
     * {@code externalMessageId = null} - el modelo ya lo admite (ver {@code ConversationMessage}).
     */
    private static String extractOutboundMessageId(WhatsAppSendMessageResponse response) {
        if (response == null || response.getMessages() == null || response.getMessages().isEmpty()) {
            return null;
        }
        return response.getMessages().get(0).getId();
    }

}
