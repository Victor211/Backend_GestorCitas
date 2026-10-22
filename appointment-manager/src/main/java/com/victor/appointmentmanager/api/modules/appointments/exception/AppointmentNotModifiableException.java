package com.victor.appointmentmanager.api.modules.appointments.exception;

import com.victor.appointmentmanager.api.common.exception.BusinessException;

/**
 * La cita existe y pertenece al cliente, pero ya no puede cancelarse ni reprogramarse desde el
 * canal del cliente (ya cancelada, completada, no-show o con inicio en el pasado).
 */
public class AppointmentNotModifiableException extends BusinessException {

    public AppointmentNotModifiableException(String message) {
        super(message);
    }
}
