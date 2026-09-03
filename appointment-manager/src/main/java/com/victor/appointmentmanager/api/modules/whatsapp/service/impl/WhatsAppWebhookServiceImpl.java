package com.victor.appointmentmanager.api.modules.whatsapp.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.victor.appointmentmanager.api.modules.ai.dto.response.ConversationResponse;
import com.victor.appointmentmanager.api.modules.ai.service.ConversationService;
import com.victor.appointmentmanager.api.modules.conversations.entity.Conversation;
import com.victor.appointmentmanager.api.modules.conversations.enums.ConversationMode;
import com.victor.appointmentmanager.api.modules.conversations.service.ConversationMessageService;
import com.victor.appointmentmanager.api.modules.whatsapp.client.WhatsAppClient;
import com.victor.appointmentmanager.api.modules.whatsapp.config.WhatsAppSignatureValidator;
import com.victor.appointmentmanager.api.modules.whatsapp.dto.response.WhatsAppSendMessageResponse;
import com.victor.appointmentmanager.api.modules.whatsapp.dto.webhook.WhatsAppInboundMessage;
import com.victor.appointmentmanager.api.modules.whatsapp.dto.webhook.WhatsAppWebhookChange;
import com.victor.appointmentmanager.api.modules.whatsapp.dto.webhook.WhatsAppWebhookEntry;
import com.victor.appointmentmanager.api.modules.whatsapp.dto.webhook.WhatsAppWebhookPayload;
import com.victor.appointmentmanager.api.modules.whatsapp.dto.webhook.WhatsAppWebhookValue;
import com.victor.appointmentmanager.api.modules.whatsapp.entity.WhatsAppInboundEvent;
import com.victor.appointmentmanager.api.modules.whatsapp.repository.WhatsAppInboundEventRepository;
import com.victor.appointmentmanager.api.modules.whatsapp.service.WhatsAppWebhookService;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class WhatsAppWebhookServiceImpl implements WhatsAppWebhookService {

    private static final String SUBSCRIBE_MODE = "subscribe";
    private static final String TEXT_MESSAGE_TYPE = "text";
    private static final String UNSUPPORTED_MESSAGE_REPLY =
            "Por el momento solo puedo procesar mensajes de texto.";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final int VISIBLE_PHONE_SUFFIX_LENGTH = 3;

    private final ObjectMapper objectMapper;
    private final WhatsAppSignatureValidator signatureValidator;
    private final BusinessRepository businessRepository;
    private final WhatsAppInboundEventRepository inboundEventRepository;
    private final WhatsAppInboundEventTracker eventTracker;
    private final ConversationService conversationService;
    private final ConversationMessageService conversationMessageService;
    private final WhatsAppClient whatsAppClient;
    private final String verifyToken;

    public WhatsAppWebhookServiceImpl(ObjectMapper objectMapper,
                                       WhatsAppSignatureValidator signatureValidator,
                                       BusinessRepository businessRepository,
                                       WhatsAppInboundEventRepository inboundEventRepository,
                                       WhatsAppInboundEventTracker eventTracker,
                                       ConversationService conversationService,
                                       ConversationMessageService conversationMessageService,
                                       WhatsAppClient whatsAppClient,
                                       @Value("${app.whatsapp.verify-token}") String verifyToken) {
        this.objectMapper = objectMapper;
        this.signatureValidator = signatureValidator;
        this.businessRepository = businessRepository;
        this.inboundEventRepository = inboundEventRepository;
        this.eventTracker = eventTracker;
        this.conversationService = conversationService;
        this.conversationMessageService = conversationMessageService;
        this.whatsAppClient = whatsAppClient;
        this.verifyToken = verifyToken;
    }

    @Override
    public boolean verifyWebhook(String mode, String verifyTokenParam) {
        return SUBSCRIBE_MODE.equals(mode) && this.verifyToken.equals(verifyTokenParam);
    }

    @Override
    public boolean isSignatureValid(String rawBody, String signatureHeader) {
        byte[] bodyBytes = rawBody != null ? rawBody.getBytes(StandardCharsets.UTF_8) : new byte[0];
        return signatureValidator.isValid(bodyBytes, signatureHeader);
    }

    @Override
    public void processWebhook(String rawBody) {
        WhatsAppWebhookPayload payload = parsePayload(rawBody);
        if (payload == null || payload.getEntry() == null) {
            return;
        }

        for (WhatsAppWebhookEntry entry : payload.getEntry()) {
            processEntry(entry);
        }
    }

    private void processEntry(WhatsAppWebhookEntry entry) {
        if (entry == null || entry.getChanges() == null) {
            return;
        }
        for (WhatsAppWebhookChange change : entry.getChanges()) {
            processChange(change);
        }
    }

    private void processChange(WhatsAppWebhookChange change) {
        if (change == null || change.getValue() == null) {
            return;
        }

        WhatsAppWebhookValue value = change.getValue();

        if (value.getStatuses() != null && !value.getStatuses().isEmpty()) {
            log.debug("Evento de estado de WhatsApp ignorado ({} elementos)", value.getStatuses().size());
        }

        List<WhatsAppInboundMessage> messages = value.getMessages();
        if (messages == null || messages.isEmpty()) {
            return;
        }

        String phoneNumberId = value.getMetadata() != null ? value.getMetadata().getPhoneNumberId() : null;

        for (WhatsAppInboundMessage message : messages) {
            processInboundMessage(phoneNumberId, message);
        }
    }

    private void processInboundMessage(String phoneNumberId, WhatsAppInboundMessage message) {
        if (message == null || message.getId() == null || message.getFrom() == null) {
            return;
        }

        if (inboundEventRepository.findByExternalMessageId(message.getId()).isPresent()) {
            log.debug("Mensaje de WhatsApp duplicado, se ignora. externalMessageId={}", message.getId());
            return;
        }

        Optional<Business> business = phoneNumberId != null
                ? businessRepository.findByWhatsappPhoneNumberIdAndActiveTrue(phoneNumberId)
                : Optional.empty();

        if (business.isEmpty()) {
            log.warn("No se encontró un Business activo para el phone_number_id recibido de WhatsApp");
            return;
        }

        String eventType = message.getType() != null ? message.getType() : "unknown";
        WhatsAppInboundEvent event = eventTracker.registerReceived(
                business.get().getId(), message.getId(), message.getFrom(), eventType);

        try {
            handleMessage(business.get(), message);
            eventTracker.markProcessed(event.getId());
            log.info("Mensaje de WhatsApp procesado. externalMessageId={}, businessId={}, senderPhone={}, "
                            + "resultado=PROCESSED",
                    event.getExternalMessageId(), business.get().getId(), maskPhone(message.getFrom()));
        } catch (RuntimeException ex) {
            String sanitized = sanitizeErrorMessage(ex);
            eventTracker.markFailed(event.getId(), sanitized);
            log.error("Error procesando mensaje de WhatsApp. externalMessageId={}, businessId={}, senderPhone={}, "
                            + "resultado=FAILED, error={}",
                    event.getExternalMessageId(), business.get().getId(), maskPhone(message.getFrom()), sanitized);
        }
    }

    private void handleMessage(Business business, WhatsAppInboundMessage message) {
        if (!TEXT_MESSAGE_TYPE.equals(message.getType()) || message.getText() == null
                || message.getText().getBody() == null || message.getText().getBody().isBlank()) {
            sendReply(business, message.getFrom(), UNSUPPORTED_MESSAGE_REPLY);
            return;
        }

        String inboundText = message.getText().getBody();
        // Se guarda el texto EXACTO recibido antes de que el bot lo procese: el historial debe
        // reflejar lo que el cliente escribió incluso si el procesamiento posterior falla.
        Conversation conversation = recordInboundHistory(business.getId(), message.getFrom(), message.getId(),
                inboundText);

        // Punto de corte MVP 3 - Fase 1: si un operador tomó la conversación, el INBOUND ya quedó
        // persistido (historial + unreadCount) arriba, pero el bot no debe procesar ni responder.
        // `conversation` es null solo si recordInboundHistory falló al persistir (ver su Javadoc);
        // en ese caso se sigue el comportamiento previo a esta fase y se procesa igual, ya que no
        // hay forma de conocer el modo real sin la fila persistida.
        if (conversation != null && conversation.getMode() == ConversationMode.HUMAN) {
            log.info("Conversation {} en modo HUMAN; respuesta automática omitida. businessId={}",
                    conversation.getId(), business.getId());
            return;
        }

        ConversationResponse response = conversationService.processChannelConversation(
                business.getId(), message.getFrom(), inboundText);

        if (response == null || response.getReply() == null || response.getReply().isBlank()) {
            log.warn("ConversationService devolvió una respuesta vacía. businessId={}", business.getId());
            return;
        }

        log.info("Intención detectada: {}", response.getIntent());
        sendReply(business, message.getFrom(), response.getReply());
    }

    private void sendReply(Business business, String recipientPhone, String replyText) {
        if (business.getWhatsappPhoneNumberId() == null) {
            log.warn("El negocio no tiene whatsappPhoneNumberId configurado. businessId={}", business.getId());
            return;
        }
        WhatsAppSendMessageResponse response =
                whatsAppClient.sendTextMessage(business.getWhatsappPhoneNumberId(), recipientPhone, replyText);
        // Se guarda solo lo que efectivamente llegó al punto de enviarse (sendTextMessage no lanzó
        // excepción): si Cloud API rechaza el envío, no se registra un OUTBOUND que nunca salió.
        recordOutboundHistory(business.getId(), recipientPhone, extractOutboundMessageId(response), replyText);
    }

    /**
     * Aísla los fallos de persistencia del historial del flujo real del bot: si guardar el
     * historial falla (ej. problema puntual de base de datos), el cliente igual debe recibir su
     * respuesta y el mensaje no debe marcarse como fallido en whatsapp_inbound_events por una causa
     * ajena al procesamiento real. El error se registra igual, nunca se descarta en silencio.
     */
    private Conversation recordInboundHistory(Long businessId, String senderPhone, String externalMessageId,
                                               String content) {
        try {
            return conversationMessageService.recordInbound(businessId, senderPhone, externalMessageId, content);
        } catch (RuntimeException ex) {
            log.error("No se pudo persistir el historial INBOUND. businessId={}, senderPhone={}, error={}",
                    businessId, maskPhone(senderPhone), ex.getMessage());
            return null;
        }
    }

    private void recordOutboundHistory(Long businessId, String recipientPhone, String externalMessageId,
                                        String content) {
        try {
            conversationMessageService.recordOutbound(businessId, recipientPhone, externalMessageId, content);
        } catch (RuntimeException ex) {
            log.error("No se pudo persistir el historial OUTBOUND. businessId={}, senderPhone={}, error={}",
                    businessId, maskPhone(recipientPhone), ex.getMessage());
        }
    }

    private static String extractOutboundMessageId(WhatsAppSendMessageResponse response) {
        if (response == null || response.getMessages() == null || response.getMessages().isEmpty()) {
            return null;
        }
        return response.getMessages().get(0).getId();
    }

    private String sanitizeErrorMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() > MAX_ERROR_MESSAGE_LENGTH ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH) : message;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() <= VISIBLE_PHONE_SUFFIX_LENGTH) {
            return "***";
        }
        String suffix = phone.substring(phone.length() - VISIBLE_PHONE_SUFFIX_LENGTH);
        return "*".repeat(phone.length() - VISIBLE_PHONE_SUFFIX_LENGTH) + suffix;
    }

    private WhatsAppWebhookPayload parsePayload(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, WhatsAppWebhookPayload.class);
        } catch (JsonProcessingException ex) {
            log.warn("No se pudo interpretar el payload del webhook de WhatsApp: {}", ex.getOriginalMessage());
            return null;
        }
    }

}
