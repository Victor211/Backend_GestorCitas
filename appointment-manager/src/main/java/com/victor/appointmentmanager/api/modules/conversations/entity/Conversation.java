package com.victor.appointmentmanager.api.modules.conversations.entity;

import com.victor.appointmentmanager.api.common.entity.BaseEntity;
import com.victor.appointmentmanager.api.modules.conversations.enums.ConversationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Conversación histórica de WhatsApp entre un negocio y un número de teléfono
 * ({@code businessId + senderPhone}, único por fila: en este MVP no hay sesiones múltiples, todo
 * el historial de un mismo número con un mismo negocio se acumula en una sola Conversation).
 *
 * <p>No debe confundirse con
 * {@link com.victor.appointmentmanager.api.modules.ai.entity.ConversationState}, que guarda el
 * estado temporal del flujo de reserva (servicio/empleado/fecha pendientes, etapa, saludo). Esta
 * entidad y {@link ConversationMessage} son exclusivamente historial: observan la conversación,
 * nunca controlan su flujo.</p>
 *
 * <p>{@code customerId} es nullable a propósito: un número puede escribir antes de existir en
 * {@code customers}. Se asocia de forma perezosa la primera vez que, al registrar un mensaje, ya
 * exista (o se acabe de crear) un Customer activo con ese teléfono - ver
 * {@code ConversationMessageService}. Los mensajes ya guardados con {@code customerId = null} no
 * se actualizan retroactivamente (decisión de simplicidad del MVP 2, ver reporte de la fase).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "conversations",
        uniqueConstraints = @UniqueConstraint(name = "uk_conversations_business_sender",
                columnNames = {"business_id", "sender_phone"}),
        indexes = {
                @Index(name = "idx_conversations_business_last_message", columnList = "business_id, last_message_at")
        })
public class Conversation extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "sender_phone", nullable = false, length = 30)
    private String senderPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_preview", length = 300)
    private String lastMessagePreview;

    /** Solo cuenta mensajes INBOUND. Sin endpoint de "marcar como leído" todavía (Fase 2). */
    @Column(name = "unread_count", nullable = false)
    private int unreadCount = 0;

}
