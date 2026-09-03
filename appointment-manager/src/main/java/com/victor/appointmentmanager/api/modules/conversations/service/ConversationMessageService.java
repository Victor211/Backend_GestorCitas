package com.victor.appointmentmanager.api.modules.conversations.service;

import com.victor.appointmentmanager.api.modules.conversations.entity.Conversation;
import com.victor.appointmentmanager.api.modules.conversations.entity.ConversationMessage;
import com.victor.appointmentmanager.api.modules.conversations.enums.ConversationMode;
import com.victor.appointmentmanager.api.modules.conversations.enums.ConversationStatus;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageDirection;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageSenderType;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageType;
import com.victor.appointmentmanager.api.modules.conversations.repository.ConversationMessageRepository;
import com.victor.appointmentmanager.api.modules.conversations.repository.ConversationRepository;
import com.victor.appointmentmanager.api.modules.customers.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Único punto de escritura del historial de conversaciones de WhatsApp (MVP 2 - Fase 1 backend).
 *
 * <p>Responsabilidad exclusiva: resolver/crear la {@link Conversation} de un
 * {@code businessId + senderPhone}, persistir cada {@link ConversationMessage} (INBOUND u
 * OUTBOUND) y mantener la metadata de bandeja ({@code lastMessageAt}, {@code lastMessagePreview},
 * {@code unreadCount}). No conoce nada de servicios, empleados, disponibilidad ni creación de
 * citas: eso sigue siendo responsabilidad exclusiva de {@code ConversationServiceImpl} /
 * {@code AppointmentService}.</p>
 *
 * <p><b>Deduplicación:</b> esta clase NO reimplementa deduplicación de mensajes de WhatsApp. La
 * fuente de verdad sigue siendo {@code WhatsAppInboundEventRepository#findByExternalMessageId}
 * (ver {@code WhatsAppWebhookServiceImpl}), que ya evita reprocesar un mismo {@code externalMessageId}
 * de Meta antes de que se llegue a llamar a este servicio. El unique constraint sobre
 * {@code conversation_messages.external_message_id} es una defensa adicional a nivel de base de
 * datos, no un segundo mecanismo de deduplicación.</p>
 *
 * <p><b>Transacciones:</b> cada método público es una transacción corta e independiente (mismo
 * patrón que {@code WhatsAppInboundEventTracker} / {@code ConversationStateStore}), pensada para
 * ser invocada desde fuera de cualquier transacción larga. El llamador (ver
 * {@code WhatsAppWebhookServiceImpl}) atrapa cualquier excepción de este servicio y solo la
 * registra en el log: una falla al persistir historial nunca debe impedir que el bot responda ni
 * marcar como fallido un mensaje que en realidad sí se procesó (ver reporte de la fase, sección de
 * transacciones).</p>
 */
@Component
@RequiredArgsConstructor
public class ConversationMessageService {

    private static final int PREVIEW_MAX_LENGTH = 200;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final CustomerRepository customerRepository;

    /**
     * @return la {@link Conversation} ya persistida, para que el llamador (ver
     * {@code WhatsAppWebhookServiceImpl}) pueda leer {@code mode} sin una consulta aparte y decidir
     * si corresponde procesar la respuesta automática del bot (MVP 3 - Fase 1).
     */
    @Transactional
    public Conversation recordInbound(Long businessId, String senderPhone, String externalMessageId, String content) {
        Conversation conversation = findOrCreateConversation(businessId, senderPhone);

        ConversationMessage message = new ConversationMessage();
        message.setConversationId(conversation.getId());
        message.setBusinessId(businessId);
        message.setCustomerId(conversation.getCustomerId());
        message.setExternalMessageId(externalMessageId);
        message.setDirection(MessageDirection.INBOUND);
        message.setSenderType(MessageSenderType.CUSTOMER);
        message.setMessageType(MessageType.TEXT);
        message.setContent(content);
        conversationMessageRepository.save(message);

        conversation.setLastMessageAt(Instant.now());
        conversation.setLastMessagePreview(preview(content));
        conversation.setUnreadCount(conversation.getUnreadCount() + 1);
        conversationRepository.save(conversation);

        return conversation;
    }

    @Transactional
    public void recordOutbound(Long businessId, String senderPhone, String externalMessageId, String content) {
        Conversation conversation = findOrCreateConversation(businessId, senderPhone);

        ConversationMessage message = new ConversationMessage();
        message.setConversationId(conversation.getId());
        message.setBusinessId(businessId);
        message.setCustomerId(conversation.getCustomerId());
        message.setExternalMessageId(externalMessageId);
        message.setDirection(MessageDirection.OUTBOUND);
        message.setSenderType(MessageSenderType.BOT);
        message.setMessageType(MessageType.TEXT);
        message.setContent(content);
        conversationMessageRepository.save(message);

        // OUTBOUND nunca incrementa unreadCount: solo cuenta lo que el cliente escribió.
        conversation.setLastMessageAt(Instant.now());
        conversation.setLastMessagePreview(preview(content));
        conversationRepository.save(conversation);
    }

    /**
     * Resuelve la Conversation de {@code businessId + senderPhone} (1 conversación por par en este
     * MVP) o la crea si es el primer mensaje. Si todavía no tiene {@code customerId} asociado,
     * intenta resolverlo contra un Customer activo ya existente con ese teléfono - esto cubre tanto
     * al cliente que ya existía antes del primer mensaje como al que se acaba de crear en el mismo
     * turno (la creación del Customer ya se confirmó en su propia transacción antes de llegar acá).
     * Una vez asociado, no se vuelve a consultar. Los mensajes ya guardados con customerId null NO
     * se actualizan retroactivamente (ver Javadoc de {@link Conversation}).
     *
     * <p><b>Importante:</b> cuando la Conversation es nueva (todavía no tiene id), se persiste ANTES
     * de devolverla. {@code conversation_messages.conversation_id} es {@code NOT NULL} y
     * {@link Conversation} usa {@code GenerationType.IDENTITY}: el id solo existe después del
     * INSERT. Devolver una Conversation transitoria (sin guardar) hacía que el primer mensaje de
     * cada {@code businessId + senderPhone} se armara con {@code conversationId = null} y violara
     * esa restricción al guardar el {@link ConversationMessage} (bug corregido: faltaba este save).
     * Para una Conversation ya existente no hace falta volver a guardarla acá: ya tiene id, y su
     * metadata (lastMessageAt/preview/unreadCount/customerId) se persiste al final de
     * {@link #recordInbound} / {@link #recordOutbound}.</p>
     */
    private Conversation findOrCreateConversation(Long businessId, String senderPhone) {
        Conversation conversation = conversationRepository.findByBusinessIdAndSenderPhone(businessId, senderPhone)
                .orElseGet(() -> newConversation(businessId, senderPhone));

        if (conversation.getCustomerId() == null) {
            customerRepository.findByBusinessIdAndPhoneAndActiveTrue(businessId, senderPhone)
                    .ifPresent(customer -> conversation.setCustomerId(customer.getId()));
        }

        if (conversation.getId() == null) {
            // SimpleJpaRepository.save() invoca EntityManager#persist() para una entidad nueva (no
            // merge): persist() asigna el id generado sobre esta misma instancia, no hace falta
            // reasignar la variable (y reasignarla rompería la lambda anterior, que la captura).
            conversationRepository.save(conversation);
        }

        return conversation;
    }

    private Conversation newConversation(Long businessId, String senderPhone) {
        Conversation conversation = new Conversation();
        conversation.setBusinessId(businessId);
        conversation.setSenderPhone(senderPhone);
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setMode(ConversationMode.BOT);
        conversation.setStartedAt(Instant.now());
        conversation.setUnreadCount(0);
        return conversation;
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        return trimmed.length() > PREVIEW_MAX_LENGTH ? trimmed.substring(0, PREVIEW_MAX_LENGTH) : trimmed;
    }

}
