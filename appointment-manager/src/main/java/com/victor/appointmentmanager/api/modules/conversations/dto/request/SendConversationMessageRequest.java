package com.victor.appointmentmanager.api.modules.conversations.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MVP 3 - Fase 2: envío manual de un mensaje de texto hacia una conversación en modo HUMAN.
 * {@code @NotBlank} ya rechaza null, vacío y "solo espacios" (hace trim internamente para validar).
 * El límite de 4096 caracteres es el máximo real de un mensaje de texto de WhatsApp Cloud API, no
 * un límite arbitrario del proyecto.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SendConversationMessageRequest {

    @NotBlank
    @Size(max = 4096)
    private String content;

}
