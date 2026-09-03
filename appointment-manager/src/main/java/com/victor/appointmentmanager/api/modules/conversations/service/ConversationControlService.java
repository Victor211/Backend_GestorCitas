package com.victor.appointmentmanager.api.modules.conversations.service;

import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationModeResponse;

/**
 * Control BOT/HUMAN de una conversación (MVP 3 - Fase 1: takeover/release). Responsabilidad
 * exclusiva: cambiar {@code Conversation.mode} para el negocio autenticado. No envía mensajes, no
 * conoce nada del flujo del bot ni de WhatsApp - eso sigue siendo de
 * {@code ConversationServiceImpl} (bot) y {@code WhatsAppWebhookServiceImpl}, que sí leen este modo
 * para decidir si responden automáticamente.
 */
public interface ConversationControlService {

    /** Pasa la conversación a HUMAN (el bot deja de responder). Idempotente. */
    ConversationModeResponse takeover(Long conversationId);

    /** Devuelve la conversación a BOT (retoma la respuesta automática). Idempotente. */
    ConversationModeResponse release(Long conversationId);

}
