package com.victor.appointmentmanager.api.modules.ai.exception;

import com.victor.appointmentmanager.api.common.exception.BusinessException;

/**
 * Se lanza cuando una hora mencionada por el cliente (ej. "a las 4") admite dos lecturas
 * razonables (04:00 o 16:00) y no hay contexto suficiente en el mensaje ni en la hora actual del
 * negocio para elegir una automáticamente. El mensaje ya viene redactado para usarse tal cual
 * como respuesta al cliente.
 */
public class AmbiguousTimeException extends BusinessException {

    public AmbiguousTimeException(int amHour, int pmHour, int minute) {
        super(String.format("¿Te referís a las %02d:%02d o a las %02d:%02d?", amHour, minute, pmHour, minute));
    }

}
