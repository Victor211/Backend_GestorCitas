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
    AWAITING_CUSTOMER_NAME
}
