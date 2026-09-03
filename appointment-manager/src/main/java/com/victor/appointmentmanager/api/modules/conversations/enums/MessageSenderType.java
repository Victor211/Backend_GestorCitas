package com.victor.appointmentmanager.api.modules.conversations.enums;

/**
 * {@code HUMAN} identifica un OUTBOUND escrito manualmente por un operador del negocio (MVP 3 -
 * Fase 2), a diferencia de {@code BOT} (respuesta automática). Nunca se reutiliza {@code BOT} para
 * un mensaje humano: son remitentes distintos aunque comparten {@code direction = OUTBOUND}.
 */
public enum MessageSenderType {
    CUSTOMER,
    BOT,
    HUMAN,
    SYSTEM
}
