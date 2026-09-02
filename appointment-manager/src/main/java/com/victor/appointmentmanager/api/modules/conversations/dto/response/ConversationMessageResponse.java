package com.victor.appointmentmanager.api.modules.conversations.dto.response;

import com.victor.appointmentmanager.api.modules.conversations.enums.MessageDirection;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageSenderType;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Un mensaje del historial, expuesto al frontend. Deliberadamente NO incluye
 * {@code externalMessageId}, {@code businessId} ni {@code customerId}: son detalles internos que el
 * frontend no necesita para renderizar un chat, y no hay ningún prompt, intent ni metadata de
 * OpenAI que exponer porque nunca se persistió (ver Fase 1).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessageResponse {

    private Long id;
    private Long conversationId;
    private MessageDirection direction;
    private MessageSenderType senderType;
    private MessageType messageType;
    private String content;
    private Instant createdAt;

}
