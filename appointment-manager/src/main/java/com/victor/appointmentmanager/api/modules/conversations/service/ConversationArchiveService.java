package com.victor.appointmentmanager.api.modules.conversations.service;

import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationArchiveResponse;

/**
 * Archivado/restauración de una conversación (MVP 3 - Fase 3). Responsabilidad exclusiva: mutar
 * {@code Conversation.archivedAt} para el negocio autenticado. No borra mensajes ni
 * {@code ConversationState}, no modifica {@code customerId}, {@code mode}, {@code status} ni
 * {@code unreadCount} - ver Javadoc de {@code Conversation.archivedAt}.
 */
public interface ConversationArchiveService {

    /** Archiva la conversación (archivedAt = ahora). Idempotente: si ya está archivada, no-op. */
    ConversationArchiveResponse archive(Long conversationId);

    /** Restaura la conversación (archivedAt = null). Idempotente: si ya está activa, no-op. */
    ConversationArchiveResponse restore(Long conversationId);

}
