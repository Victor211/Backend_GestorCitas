package com.victor.appointmentmanager.api.modules.conversations.enums;

/**
 * Preparado para la bandeja de conversaciones de la Fase 2 (marcar como leída, cerrar, etc.). En
 * esta fase toda Conversation se crea y permanece en ACTIVE: no hay ningún flujo que la transicione
 * a CLOSED todavía.
 */
public enum ConversationStatus {
    ACTIVE,
    CLOSED
}
