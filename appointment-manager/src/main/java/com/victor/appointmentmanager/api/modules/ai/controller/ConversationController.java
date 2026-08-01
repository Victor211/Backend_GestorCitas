package com.victor.appointmentmanager.api.modules.ai.controller;

import com.victor.appointmentmanager.api.common.exception.ApiError;
import com.victor.appointmentmanager.api.common.response.ApiResponse;
import com.victor.appointmentmanager.api.common.response.ResponseFactory;
import com.victor.appointmentmanager.api.modules.ai.dto.request.ConversationRequest;
import com.victor.appointmentmanager.api.modules.ai.dto.response.ConversationResponse;
import com.victor.appointmentmanager.api.modules.ai.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "Motor conversacional del sistema. Independiente del canal de comunicación: "
        + "hoy se expone solo vía API REST, en el futuro podrá conectarse a WhatsApp, Web Chat, Telegram, etc.")
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/conversation")
    @Operation(summary = "Procesar un mensaje conversacional",
            description = "Interpreta un mensaje en lenguaje natural, detecta la intención (GREETING, "
                    + "BOOK_APPOINTMENT, RESCHEDULE_APPOINTMENT, CANCEL_APPOINTMENT, CHECK_AVAILABILITY o "
                    + "UNKNOWN) y, cuando corresponde y hay información suficiente, ejecuta la acción "
                    + "correspondiente reutilizando el AppointmentService existente.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Respuesta generada correctamente"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Negocio no encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "El asistente de IA no pudo generar una respuesta",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<ConversationResponse> conversation(@Valid @RequestBody ConversationRequest request) {
        return ResponseFactory.success(conversationService.handleMessage(request));
    }

}
