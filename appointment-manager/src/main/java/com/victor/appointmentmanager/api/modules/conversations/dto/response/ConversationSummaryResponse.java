package com.victor.appointmentmanager.api.modules.conversations.dto.response;

import com.victor.appointmentmanager.api.modules.conversations.enums.ConversationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Fila de la bandeja de conversaciones. {@code customerId}/{@code customerName} son {@code null}
 * cuando la conversación todavía no tiene un Customer asociado (ver Fase 1): el frontend debe usar
 * {@code senderPhone} como fallback en ese caso.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryResponse {

    private Long id;
    private Long customerId;
    private String customerName;
    private String senderPhone;
    private ConversationStatus status;
    private Instant lastMessageAt;
    private String lastMessagePreview;
    private int unreadCount;

}
