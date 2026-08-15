package com.victor.appointmentmanager.api.modules.ai.enums;

/**
 * Resultado de clasificar (en backend, sin IA) la respuesta del cliente cuando la conversación
 * está esperando confirmación de una reserva propuesta.
 */
public enum ConfirmationSignal {
    /** "sí", "dale", "confirmo", etc.: crear la cita propuesta. */
    POSITIVE,
    /** "no", "cancelar", "mejor no", "cambiemos": descartar la propuesta por completo. */
    NEGATIVE_FULL,
    /** "otra hora", "otro día": descartar solo la fecha/hora propuesta. */
    NEGATIVE_TIME,
    /** "otro profesional": descartar solo el empleado propuesto. */
    NEGATIVE_EMPLOYEE,
    /** No matchea ninguna palabra clave: probablemente el cliente está modificando un dato. */
    UNRECOGNIZED
}
