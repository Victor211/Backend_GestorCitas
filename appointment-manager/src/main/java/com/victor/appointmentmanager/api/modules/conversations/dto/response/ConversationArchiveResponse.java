package com.victor.appointmentmanager.api.modules.conversations.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Respuesta de los endpoints de archivado (MVP 3 - Fase 3: archive/restore). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationArchiveResponse {

    private Long id;
    private Instant archivedAt;

}
