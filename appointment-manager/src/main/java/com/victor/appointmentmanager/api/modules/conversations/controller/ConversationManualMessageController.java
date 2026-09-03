package com.victor.appointmentmanager.api.modules.conversations.controller;

import com.victor.appointmentmanager.api.common.exception.ApiError;
import com.victor.appointmentmanager.api.common.response.ApiResponse;
import com.victor.appointmentmanager.api.common.response.ResponseFactory;
import com.victor.appointmentmanager.api.modules.conversations.dto.request.SendConversationMessageRequest;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationMessageResponse;
import com.victor.appointmentmanager.api.modules.conversations.service.ConversationManualMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Envío manual de mensajes de texto hacia WhatsApp (MVP 3 - Fase 2). Separado de
 * {@link ConversationHistoryController} (solo lectura) y de {@link ConversationControlController}
 * (cambia {@code mode}, nunca envía mensajes): este controller solo envía y persiste OUTBOUND, sin
 * tocar {@code Conversation.mode} - el operador debe hacer takeover explícito antes.
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversations", description = "Envío manual de mensajes en conversaciones del negocio autenticado")
public class ConversationManualMessageController {

    private final ConversationManualMessageService conversationManualMessageService;

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enviar un mensaje manual a una conversación en modo HUMAN",
            description = "Envía el texto mediante WhatsApp Cloud API usando Conversation.senderPhone como "
                    + "destinatario (nunca un teléfono del request) y, solo si el envío fue exitoso, persiste el "
                    + "OUTBOUND con senderType HUMAN. Requiere que la conversación ya esté en modo HUMAN "
                    + "(ver PUT /{id}/takeover); no cambia el modo por sí mismo.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "Mensaje enviado y persistido"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "content ausente, vacío o demasiado largo",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Conversación no encontrada en ese negocio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "La conversación está en modo BOT, o WhatsApp Cloud API "
                            + "rechazó el envío",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<ConversationMessageResponse> sendMessage(
            @PathVariable Long id, @Valid @RequestBody SendConversationMessageRequest request) {
        ConversationMessageResponse sent = conversationManualMessageService.sendMessage(id, request.getContent());
        return ResponseFactory.created("Mensaje enviado", sent);
    }

}
