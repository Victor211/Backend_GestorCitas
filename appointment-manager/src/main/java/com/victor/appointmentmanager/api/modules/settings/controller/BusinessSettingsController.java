package com.victor.appointmentmanager.api.modules.settings.controller;

import com.victor.appointmentmanager.api.common.exception.ApiError;
import com.victor.appointmentmanager.api.common.response.ApiResponse;
import com.victor.appointmentmanager.api.common.response.ResponseFactory;
import com.victor.appointmentmanager.api.modules.settings.dto.request.UpdateBusinessSettingsRequest;
import com.victor.appointmentmanager.api.modules.settings.dto.response.BusinessSettingsResponse;
import com.victor.appointmentmanager.api.modules.settings.service.BusinessSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/business")
@RequiredArgsConstructor
@Tag(name = "Business Settings", description = "Configuración general del negocio autenticado (nombre, contacto, "
        + "dirección y zona horaria) para la pantalla Settings del frontend. Los secretos de OpenAI y WhatsApp "
        + "no se gestionan ni se exponen desde esta API.")
public class BusinessSettingsController {

    private final BusinessSettingsService businessSettingsService;

    @GetMapping
    @Operation(summary = "Obtiene la configuración general del negocio autenticado.",
            description = "El Business se obtiene exclusivamente a partir del usuario autenticado (JWT); "
                    + "nunca se recibe un businessId desde el cliente.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Configuración obtenida correctamente"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "No autenticado o token inválido",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Negocio no encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<BusinessSettingsResponse> getSettings() {
        return ResponseFactory.success(businessSettingsService.getSettings());
    }

    @PutMapping
    @Operation(summary = "Actualiza la configuración general del negocio autenticado.",
            description = "Solo permite modificar name, phone, email, address y timezone. La zona horaria debe "
                    + "ser un ZoneId válido (java.time.ZoneId); id, active, createdAt, updatedAt y los "
                    + "identificadores de WhatsApp no son editables desde este endpoint.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Configuración actualizada correctamente"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Datos inválidos o zona horaria no reconocida",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "No autenticado o token inválido",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Negocio no encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<BusinessSettingsResponse> updateSettings(
            @Valid @RequestBody UpdateBusinessSettingsRequest request) {
        BusinessSettingsResponse updated = businessSettingsService.updateSettings(request);
        return ResponseFactory.success("Configuración actualizada exitosamente", updated);
    }

}
