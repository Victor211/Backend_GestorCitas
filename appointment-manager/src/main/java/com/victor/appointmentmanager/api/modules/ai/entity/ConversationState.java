package com.victor.appointmentmanager.api.modules.ai.entity;

import com.victor.appointmentmanager.api.common.entity.BaseEntity;
import com.victor.appointmentmanager.api.modules.ai.enums.ConversationStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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

    /**
     * Último Employee mencionado o resuelto en la conversación (por nombre explícito,
     * auto-asignación sin ambigüedad, o consulta de disponibilidad), aunque todavía no sea el
     * empleado elegido para la reserva en curso. Permite resolver referencias pronominales como
     * "con él"/"el mismo" sin volver a preguntar el profesional. No se limpia junto con el
     * borrador de reserva: es memoria conversacional de más largo alcance que un intento puntual.
     */
    @Column(name = "last_referenced_employee_id")
    private Long lastReferencedEmployeeId;

    @Column(name = "last_referenced_employee_name", length = 200)
    private String lastReferencedEmployeeName;

    /**
     * Marca transitoria (no persistida) que {@code ConversationStateStore} activa cuando el
     * estado recuperado estaba en {@code AWAITING_CONFIRMATION} pero venció por inactividad. Le
     * permite al orquestador responder "esa propuesta ya venció" en lugar de crear una cita a
     * partir de un "sí" desfasado, sin necesitar una columna nueva en base de datos.
     */
    @Transient
    private boolean expiredPendingConfirmation = false;

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

    /**
     * Trata la conversación como si empezara de cero: limpia el borrador de reserva y también el
     * saludo/nombre-en-espera y la última referencia pronominal, ya que todos pertenecen a una
     * interacción que ya se considera abandonada. La identidad (customerId/businessId/phone) NO
     * se toca: {@code sender_phone -> Customer} es permanente y se vuelve a resolver contra la
     * base real en cada turno, independientemente de la vigencia de este estado.
     */
    public void resetForNewConversation() {
        clearBookingDraft();
        this.greeted = false;
        this.awaitingName = false;
        this.lastReferencedEmployeeId = null;
        this.lastReferencedEmployeeName = null;
    }

}
