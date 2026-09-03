package com.victor.appointmentmanager.api.modules.conversations.exception;

import com.victor.appointmentmanager.api.common.exception.BusinessException;

/**
 * Se lanza al intentar enviar un mensaje manual (MVP 3 - Fase 2) sobre una Conversation en modo
 * BOT. {@link BusinessException} ya mapea a 409 Conflict en {@code GlobalExceptionHandler}: el
 * estado del recurso (modo BOT) es incompatible con la operación pedida, no un dato inválido
 * (400) ni un recurso inexistente (404). El operador debe hacer takeover explícito primero -
 * este endpoint nunca cambia el modo por sí mismo.
 */
public class ConversationNotInHumanModeException extends BusinessException {

    public ConversationNotInHumanModeException(String message) {
        super(message);
    }

}
