package com.victor.appointmentmanager.api.modules.conversations.service;

import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationMessageResponse;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationReadResponse;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * API de lectura del historial de conversaciones (MVP 2 - Fase 2) para el panel autenticado.
 * Responsabilidad exclusiva: listar/consultar lo que {@code ConversationMessageService} (Fase 1)
 * ya persistió, y marcar una conversación como leída. No conoce nada del flujo del bot ni de
 * WhatsApp - eso sigue siendo de {@code ConversationServiceImpl} (bot) y
 * {@code WhatsAppWebhookServiceImpl}.
 */
public interface ConversationQueryService {

    Page<ConversationSummaryResponse> listConversations(Pageable pageable);

    Page<ConversationMessageResponse> listMessages(Long conversationId, Pageable pageable);

    ConversationReadResponse markAsRead(Long conversationId);

}
