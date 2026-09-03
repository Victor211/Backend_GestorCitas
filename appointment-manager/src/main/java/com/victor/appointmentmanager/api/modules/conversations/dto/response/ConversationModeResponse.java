package com.victor.appointmentmanager.api.modules.conversations.dto.response;

import com.victor.appointmentmanager.api.modules.conversations.enums.ConversationMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Respuesta de los endpoints de control BOT/HUMAN (MVP 3 - Fase 1: takeover/release). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationModeResponse {

    private Long id;
    private ConversationMode mode;

}
