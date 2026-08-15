package com.victor.appointmentmanager.api.modules.ai.exception;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Se lanza cuando la expresión del cliente indica una fecha válida (ej. "hoy", "mañana", "el
 * próximo lunes") pero no incluye una hora. Permite que el orquestador conversacional recuerde
 * la fecha ya resuelta y solo pregunte por la hora, sin volver a pedir la fecha.
 */
@Getter
public class MissingTimeException extends BusinessException {

    private final LocalDate resolvedDate;

    public MissingTimeException(LocalDate resolvedDate) {
        super("¿A qué hora te gustaría?");
        this.resolvedDate = resolvedDate;
    }

}
