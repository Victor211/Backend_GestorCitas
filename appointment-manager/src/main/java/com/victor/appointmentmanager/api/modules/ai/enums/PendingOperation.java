package com.victor.appointmentmanager.api.modules.ai.enums;

/**
 * Operación sobre una cita YA existente del cliente que está en curso en la conversación. Vive
 * separada del borrador de reserva nueva (pendingServiceId/pendingEmployeeId/...): ambos pueden
 * coexistir sin pisarse.
 */
public enum PendingOperation {
    CANCEL,
    RESCHEDULE
}
