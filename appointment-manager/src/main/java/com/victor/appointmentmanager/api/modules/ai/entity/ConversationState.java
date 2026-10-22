package com.victor.appointmentmanager.api.modules.ai.entity;

import com.victor.appointmentmanager.api.common.entity.BaseEntity;
import com.victor.appointmentmanager.api.modules.ai.enums.ConversationStage;
import com.victor.appointmentmanager.api.modules.ai.enums.PendingOperation;
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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

    // --- Operación sobre una cita existente (cancelar/reprogramar), separada del borrador de reserva ---

    @Enumerated(EnumType.STRING)
    @Column(name = "pending_operation", length = 20)
    private PendingOperation pendingOperation;

    /** Cita propia sobre la que se opera; se revalida (propiedad, estado) en cada paso y al ejecutar. */
    @Column(name = "pending_operation_appointment_id")
    private Long pendingOperationAppointmentId;

    /** Ids (separados por coma, en el orden mostrado) de las citas propias ofrecidas para elegir. */
    @Column(name = "pending_operation_options", length = 200)
    private String pendingOperationOptions;

    /** Nuevo horario ya resuelto y validado, propuesto en una reprogramación. */
    @Column(name = "pending_operation_new_start_at")
    private Instant pendingOperationNewStartAt;

    /** Nueva fecha ya resuelta cuando el cliente todavía no indicó la hora de la reprogramación. */
    @Column(name = "pending_operation_new_date")
    private LocalDate pendingOperationNewDate;

    /**
     * Ids reales de los Services tal como se numeraron en el último catálogo mostrado al cliente,
     * en orden (la posición 1 es el primer id), separados por coma. Es la correspondencia entre
     * "respondé con el número" y el servicio real: se persiste porque cada mensaje es una
     * request independiente y el catálogo puede cambiar entre el listado y la respuesta. No nulo
     * solo mientras hay una selección de servicio pendiente.
     */
    @Column(name = "offered_service_ids", length = 1000)
    private String offeredServiceIds;

    /**
     * Marca transitoria (no persistida) que {@code ConversationStateStore} activa cuando el
     * estado recuperado estaba en {@code AWAITING_CONFIRMATION} o {@code AWAITING_CUSTOMER_NAME}
     * pero venció por inactividad. Le permite al orquestador responder "esa propuesta ya venció"
     * en lugar de crear una cita a partir de un "sí" o un nombre desfasados, sin necesitar una
     * columna nueva en base de datos.
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

    public boolean hasPendingOperation() {
        return pendingOperation != null;
    }

    public List<Long> pendingOperationOptionList() {
        if (pendingOperationOptions == null || pendingOperationOptions.isBlank()) {
            return List.of();
        }
        return Arrays.stream(pendingOperationOptions.split(",")).map(String::trim).map(Long::valueOf).toList();
    }

    public void offerPendingOperationOptions(List<Long> appointmentIds) {
        this.pendingOperationOptions = appointmentIds.stream().map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    /**
     * Descarta cualquier operación sobre una cita existente. No toca el borrador de reserva
     * nueva; si la etapa actual era de una operación, vuelve a COLLECTING.
     */
    public void clearPendingOperation() {
        this.pendingOperation = null;
        this.pendingOperationAppointmentId = null;
        this.pendingOperationOptions = null;
        this.pendingOperationNewStartAt = null;
        this.pendingOperationNewDate = null;
        if (stage != null && stage.isPendingOperationStage()) {
            this.stage = ConversationStage.COLLECTING;
        }
    }

    public boolean hasOfferedServices() {
        return offeredServiceIds != null && !offeredServiceIds.isBlank();
    }

    public List<Long> offeredServiceIdList() {
        if (!hasOfferedServices()) {
            return List.of();
        }
        return Arrays.stream(offeredServiceIds.split(",")).map(String::trim).map(Long::valueOf).toList();
    }

    public void offerServices(List<Long> serviceIds) {
        this.offeredServiceIds = serviceIds.isEmpty() ? null
                : serviceIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public void clearOfferedServices() {
        this.offeredServiceIds = null;
    }

    public void clearBookingDraft() {
        this.offeredServiceIds = null;
        this.pendingServiceId = null;
        this.pendingEmployeeId = null;
        this.pendingDate = null;
        this.pendingStartAt = null;
        this.stage = ConversationStage.COLLECTING;
    }

    /**
     * Trata la conversación como si empezara de cero: limpia el borrador de reserva y también el
     * saludo y la última referencia pronominal, ya que ambos pertenecen a una interacción que ya
     * se considera abandonada. La identidad (customerId/businessId/phone) NO se toca:
     * {@code sender_phone -> Customer} es permanente y se vuelve a resolver contra la base real en
     * cada turno, independientemente de la vigencia de este estado.
     */
    public void resetForNewConversation() {
        clearBookingDraft();
        clearPendingOperation();
        this.greeted = false;
        this.lastReferencedEmployeeId = null;
        this.lastReferencedEmployeeName = null;
    }

}
