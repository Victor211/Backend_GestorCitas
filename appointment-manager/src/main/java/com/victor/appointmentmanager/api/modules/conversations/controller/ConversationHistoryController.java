package com.victor.appointmentmanager.api.modules.conversations.controller;

import com.victor.appointmentmanager.api.common.exception.ApiError;
import com.victor.appointmentmanager.api.common.response.ApiResponse;
import com.victor.appointmentmanager.api.common.response.ResponseFactory;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationMessageResponse;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationReadResponse;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationSummaryResponse;
import com.victor.appointmentmanager.api.modules.conversations.service.ConversationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de lectura del historial de conversaciones de WhatsApp (MVP 2 - Fase 2). Exclusivamente
 * consulta lo que {@code ConversationMessageService} (Fase 1) ya persiste en cada webhook; no
 * afecta el flujo del bot ni el envío de mensajes.
 *
 * <p>Nombrada {@code ConversationHistoryController} (y no {@code ConversationController}, el
 * nombre "obvio" para el recurso REST {@code /api/conversations}) porque ya existe
 * {@link com.victor.appointmentmanager.api.modules.ai.controller.ConversationController} para el
 * motor conversacional del bot (ruta {@code /api/ai/conversation}). Ambas clases con el mismo
 * simple name generan el mismo nombre de bean por defecto en Spring
 * ({@code conversationController}) y chocan al arrancar el contexto completo
 * ({@code ConflictingBeanDefinitionException}), aunque vivan en paquetes distintos. Se optó por
 * renombrar esta clase nueva en vez de tocar la existente del bot.</p>
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversations", description = "Historial de conversaciones de WhatsApp del negocio autenticado")
public class ConversationHistoryController {

    private final ConversationQueryService conversationQueryService;

    @GetMapping
    @Operation(summary = "Listar conversaciones del negocio autenticado",
            description = "Paginado, ordenado por lastMessageAt descendente (la más reciente primero) por "
                    + "defecto. Ejemplo: /api/conversations?page=0&size=20")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Listado obtenido correctamente"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<Page<ConversationSummaryResponse>> findAll(
            @PageableDefault(sort = "lastMessageAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseFactory.success(conversationQueryService.listConversations(pageable));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Listar mensajes de una conversación del negocio autenticado",
            description = "Paginado, ordenado por createdAt ascendente (orden cronológico, igual que un chat) "
                    + "por defecto: la página 0 trae los mensajes más antiguos. Para pedir mensajes más "
                    + "recientes se avanza de página (page=1, 2, ...); no hay todavía infinite scroll ni cursor "
                    + "por fecha, eso queda para una fase posterior. Ejemplo: "
                    + "/api/conversations/15/messages?page=0&size=50")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Listado obtenido correctamente"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Conversación no encontrada en ese negocio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<Page<ConversationMessageResponse>> findMessages(
            @PathVariable Long id,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseFactory.success(conversationQueryService.listMessages(id, pageable));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Marcar una conversación como leída",
            description = "Pone unreadCount en 0. No trackea readAt por mensaje individual todavía.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Conversación marcada como leída"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Conversación no encontrada en ese negocio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<ConversationReadResponse> markAsRead(@PathVariable Long id) {
        return ResponseFactory.success("Conversación marcada como leída", conversationQueryService.markAsRead(id));
    }

}
