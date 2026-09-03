package com.victor.appointmentmanager.api.modules.conversations.enums;

/**
 * Quién controla la conversación en este momento: el bot (respuesta automática) o un operador
 * humano que la tomó desde el panel (MVP 3 - Fase 1). No debe confundirse con
 * {@link ConversationStatus} (ACTIVE/CLOSED, ciclo de vida de la conversación) ni con
 * {@code ConversationStage} (etapa interna del flujo de reserva del bot).
 */
public enum ConversationMode {
    BOT,
    HUMAN
}
