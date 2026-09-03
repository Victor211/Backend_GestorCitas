package com.victor.appointmentmanager.api.modules.conversations.service;

import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationMessageResponse;

/**
 * Envío manual de mensajes de texto hacia WhatsApp (MVP 3 - Fase 2). Responsabilidad exclusiva:
 * validar ownership/modo de una Conversation del negocio autenticado, enviar el texto mediante el
 * mismo {@code WhatsAppClient} que ya usa el bot y, solo si el envío fue exitoso, persistir el
 * OUTBOUND vía {@code ConversationMessageService} (MVP 2). No conoce nada del flujo del bot: eso
 * sigue siendo de {@code ConversationServiceImpl} / {@code WhatsAppWebhookServiceImpl}.
 */
public interface ConversationManualMessageService {

    ConversationMessageResponse sendMessage(Long conversationId, String content);

}
