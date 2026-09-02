package com.victor.appointmentmanager.api.modules.conversations.entity;

import com.victor.appointmentmanager.api.common.entity.BaseEntity;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageDirection;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageSenderType;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un mensaje individual, entrante o saliente, dentro de una {@link Conversation}.
 *
 * <p>{@code content} siempre es el texto real: para INBOUND, exactamente lo que Meta entregó en
 * {@code text.body}; para OUTBOUND, el texto final que efectivamente se envió al cliente mediante
 * WhatsApp Cloud API - nunca JSON de OpenAI, intent, entidades extraídas ni ningún dato intermedio
 * del razonamiento del bot.</p>
 *
 * <p>{@code externalMessageId} es el {@code id} de WhatsApp del mensaje. Para INBOUND siempre está
 * presente y es la misma clave que ya usa la deduplicación existente en
 * {@code whatsapp_inbound_events} (no se reimplementa acá, solo se reutiliza el valor). Para
 * OUTBOUND puede ser {@code null} si la respuesta de Cloud API no trae el id del mensaje enviado.
 * Es único cuando no es nulo: Postgres/H2 no aplican la restricción UNIQUE entre múltiples NULLs,
 * así que muchos OUTBOUND sin id conviven sin problema.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "conversation_messages",
        indexes = {
                @Index(name = "idx_conv_messages_conversation_created", columnList = "conversation_id, created_at"),
                @Index(name = "idx_conv_messages_business_created", columnList = "business_id, created_at")
        })
public class ConversationMessage extends BaseEntity {

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "external_message_id", length = 100, unique = true)
    private String externalMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 10)
    private MessageSenderType senderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 10)
    private MessageType messageType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

}
