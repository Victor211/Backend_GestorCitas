package com.victor.appointmentmanager.api.modules.conversations.controller;

import com.victor.appointmentmanager.api.common.exception.ApiError;
import com.victor.appointmentmanager.api.common.response.ApiResponse;
import com.victor.appointmentmanager.api.common.response.ResponseFactory;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationArchiveResponse;
import com.victor.appointmentmanager.api.modules.conversations.service.ConversationArchiveService;
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
 * Archivado/restauración de conversaciones (MVP 3 - Fase 3). Separado de
 * {@link ConversationControlController} (ese cambia {@code mode}, no {@code archivedAt}) y de
 * {@link ConversationHistoryController} (solo lectura): estos endpoints exclusivamente mutan
 * {@code Conversation.archivedAt}, sin tocar historial, mode, status ni unreadCount. No implementa
 * todavía eliminación definitiva (queda para una fase posterior).
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversations", description = "Archivado/restauración de conversaciones del negocio autenticado")
public class ConversationArchiveController {

    private final ConversationArchiveService conversationArchiveService;

    @PutMapping("/{id}/archive")
    @Operation(summary = "Archivar una conversación",
            description = "Pone archivedAt en el instante actual: la conversación deja de aparecer en "
                    + "GET /api/conversations (o ?archived=false) y pasa a listarse en ?archived=true. No borra "
                    + "mensajes ni cambia mode/status/unreadCount. Idempotente: si ya estaba archivada, conserva "
                    + "su archivedAt original.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Conversación archivada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Conversación no encontrada en ese negocio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<ConversationArchiveResponse> archive(@PathVariable Long id) {
        return ResponseFactory.success("Conversación archivada", conversationArchiveService.archive(id));
    }

    @PutMapping("/{id}/restore")
    @Operation(summary = "Restaurar una conversación archivada",
            description = "Pone archivedAt en null: la conversación vuelve a aparecer en la bandeja principal "
                    + "(GET /api/conversations o ?archived=false). Idempotente.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Conversación restaurada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Conversación no encontrada en ese negocio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<ConversationArchiveResponse> restore(@PathVariable Long id) {
        return ResponseFactory.success("Conversación restaurada", conversationArchiveService.restore(id));
    }

}
