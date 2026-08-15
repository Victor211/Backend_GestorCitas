package com.victor.appointmentmanager.api.modules.ai.enums;

/**
 * Estado de la conversación para una combinación (business, customerPhone). Controla si el
 * próximo mensaje entrante debe interpretarse como una respuesta libre (slot-filling normal) o
 * como una confirmación/rechazo de una reserva ya propuesta.
 */
public enum ConversationStage {
    COLLECTING,
    AWAITING_CONFIRMATION
}
