package com.victor.appointmentmanager.api.modules.ai.entity;

import com.victor.appointmentmanager.api.common.entity.BaseEntity;
import com.victor.appointmentmanager.api.modules.ai.enums.ConversationStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Memoria conversacional mínima por (business, customerPhone): qué datos de una reserva ya se
 * recopilaron, si ya se saludó al cliente en esta conversación, y si hay una propuesta esperando
 * confirmación explícita. No reemplaza ninguna fuente de verdad de negocio: los ids apuntan a
 * entidades reales que se vuelven a validar al confirmar.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "conversation_states",
        uniqueConstraints = @UniqueConstraint(columnNames = {"business_id", "customer_phone"}))
public class ConversationState extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "customer_phone", nullable = false, length = 30)
    private String customerPhone;

    @Column(name = "customer_id")
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConversationStage stage = ConversationStage.COLLECTING;

    @Column(nullable = false)
    private boolean greeted = false;

    @Column(nullable = false)
    private boolean awaitingName = false;

    @Column(name = "pending_service_id")
    private Long pendingServiceId;

    @Column(name = "pending_employee_id")
    private Long pendingEmployeeId;

    /**
     * Fecha ya resuelta (ej. "hoy" ya interpretada con Business.timezone) cuando el cliente
     * todavía no indicó la hora. Se combina con el próximo mensaje para completar
     * {@link #pendingStartAt} sin volver a preguntar la fecha.
     */
    @Column(name = "pending_date")
    private LocalDate pendingDate;

    @Column(name = "pending_start_at")
    private Instant pendingStartAt;

    @Column(name = "pending_notes", length = 500)
    private String pendingNotes;

    public boolean hasCustomer() {
        return customerId != null;
    }

    public boolean hasPendingService() {
        return pendingServiceId != null;
    }

    public boolean hasPendingStartAt() {
        return pendingStartAt != null;
    }

    public boolean hasPendingEmployee() {
        return pendingEmployeeId != null;
    }

    public void clearBookingDraft() {
        this.pendingServiceId = null;
        this.pendingEmployeeId = null;
        this.pendingDate = null;
        this.pendingStartAt = null;
        this.pendingNotes = null;
        this.stage = ConversationStage.COLLECTING;
    }

}
