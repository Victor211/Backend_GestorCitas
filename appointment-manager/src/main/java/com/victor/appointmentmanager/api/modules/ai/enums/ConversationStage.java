package com.victor.appointmentmanager.api.modules.ai.enums;

/**
 * Estado de la conversación para una combinación (business, customerPhone). Controla cómo debe
 * interpretarse el próximo mensaje entrante.
 */
public enum ConversationStage {

    /**
     * Slot-filling libre: el mensaje se interpreta con la IA y se fusiona contra el borrador de
     * reserva. Sirve igual a un Customer ya identificado que a uno todavía desconocido — la
     * identidad del cliente no es un prerequisito para recolectar servicio/fecha/hora/profesional.
     */
    COLLECTING,

    /**
     * Hay una propuesta completa (servicio + profesional + horario) esperando un sí/no explícito,
     * clasificado de forma determinística por {@code ConfirmationClassifier} (nunca por la IA).
     */
    AWAITING_CONFIRMATION,

    /**
     * La propuesta ya fue confirmada (sí explícito) pero el Customer todavía no existe. Es la
     * única etapa en la que el próximo mensaje, sea cual sea su contenido, se interpreta
     * incondicionalmente como el nombre del cliente. Se entra aquí solo desde una confirmación
     * POSITIVE con customerId nulo, con el borrador ya resuelto e intacto; nunca por el solo hecho
     * de que el Customer sea desconocido.
     */
    AWAITING_CUSTOMER_NAME,

    /**
     * Cancelación o reprogramación (ver {@code ConversationState#getPendingOperation()}) y el
     * cliente tiene varias citas futuras: se le mostró una lista numerada y se espera que elija una.
     */
    AWAITING_APPOINTMENT_CHOICE,

    /** Cancelación: se mostró la cita propia a cancelar y se espera un sí/no inequívoco. */
    AWAITING_CANCEL_CONFIRM,

    /** Reprogramación: cita propia ya identificada, falta (o hay que corregir) la nueva fecha/hora. */
    AWAITING_RESCHEDULE_TIME,

    /** Reprogramación: se mostró cita original + nuevo horario disponible; se espera sí/no inequívoco. */
    AWAITING_RESCHEDULE_CONFIRM;

    /** Etapas de una operación sobre una cita existente (no de una reserva nueva). */
    public boolean isPendingOperationStage() {
        return this == AWAITING_APPOINTMENT_CHOICE || this == AWAITING_CANCEL_CONFIRM
                || this == AWAITING_RESCHEDULE_TIME || this == AWAITING_RESCHEDULE_CONFIRM;
    }
}
