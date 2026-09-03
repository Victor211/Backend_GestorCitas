package com.victor.appointmentmanager.api.modules.conversations.controller;

import com.victor.appointmentmanager.api.common.exception.ApiError;
import com.victor.appointmentmanager.api.common.response.ApiResponse;
import com.victor.appointmentmanager.api.common.response.ResponseFactory;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationModeResponse;
import com.victor.appointmentmanager.api.modules.conversations.service.ConversationControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Control BOT/HUMAN de una conversación (MVP 3 - Fase 1). Separado de
 * {@link ConversationHistoryController} porque ese controller es exclusivamente de lectura del
 * historial; estos endpoints, en cambio, mutan {@code Conversation.mode}. No implementa todavía
 * envío manual de mensajes (Fase 2).
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversations", description = "Control BOT/HUMAN de conversaciones del negocio autenticado")
public class ConversationControlController {

    private final ConversationControlService conversationControlService;

    @PutMapping("/{id}/takeover")
    @Operation(summary = "Tomar una conversación (HUMAN)",
            description = "Pasa la conversación a modo HUMAN: el bot deja de responder automáticamente. "
                    + "El historial y unreadCount siguen actualizándose con normalidad. Idempotente.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Conversación en modo HUMAN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Conversación no encontrada en ese negocio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<ConversationModeResponse> takeover(@PathVariable Long id) {
        return ResponseFactory.success("Conversación tomada", conversationControlService.takeover(id));
    }

    @PutMapping("/{id}/release")
    @Operation(summary = "Devolver una conversación al bot (BOT)",
            description = "Pasa la conversación a modo BOT: retoma la respuesta automática. Idempotente.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Conversación en modo BOT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Conversación no encontrada en ese negocio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<ConversationModeResponse> release(@PathVariable Long id) {
        return ResponseFactory.success("Conversación devuelta al bot", conversationControlService.release(id));
    }

}
