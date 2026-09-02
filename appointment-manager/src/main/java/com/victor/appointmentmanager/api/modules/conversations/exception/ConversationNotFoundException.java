package com.victor.appointmentmanager.api.modules.conversations.exception;

import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;

/**
 * Se lanza tanto cuando el id no existe como cuando pertenece a otro business: en ambos casos se
 * responde 404 (ver {@code GlobalExceptionHandler}), para no revelar que el recurso existe en un
 * negocio ajeno.
 */
public class ConversationNotFoundException extends ResourceNotFoundException {

    public ConversationNotFoundException(String message) {
        super(message);
    }

}
