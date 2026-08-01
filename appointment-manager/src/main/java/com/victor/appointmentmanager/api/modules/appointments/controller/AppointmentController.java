package com.victor.appointmentmanager.api.modules.appointments.controller;

import com.victor.appointmentmanager.api.common.exception.ApiError;
import com.victor.appointmentmanager.api.common.response.ApiResponse;
import com.victor.appointmentmanager.api.common.response.ResponseFactory;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.CreateAppointmentRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.RescheduleAppointmentRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.UpdateAppointmentStatusRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.response.AppointmentResponse;
import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
import com.victor.appointmentmanager.api.modules.appointments.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@Tag(name = "Appointments", description = "Gestión de citas: creación, consulta, reprogramación y cancelación. "
        + "Los campos de fecha/hora (startAt, endAt, from, to) usan formato Instant en UTC, "
        + "ejemplo: 2026-08-01T14:30:00Z")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una nueva cita",
            description = "Calcula automáticamente endAt a partir de la duración del servicio. "
                    + "La cita se crea con estado CONFIRMED.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "Cita creada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Datos inválidos o fecha en el pasado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Business, Customer, Employee o Service no encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Cita fuera de horario, superpuesta, o relación "
                            + "perteneciente a otro negocio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentResponse created = appointmentService.create(request);
        return ResponseFactory.created("Cita creada exitosamente", created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una cita por su id, dentro de un negocio")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Cita encontrada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "businessId es obligatorio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Cita no encontrada en ese negocio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<AppointmentResponse> findById(
            @PathVariable Long id,
            @Parameter(description = "Id del negocio (obligatorio)")
            @RequestParam(required = false) Long businessId) {
        return ResponseFactory.success(appointmentService.findById(id, businessId));
    }

    @GetMapping
    @Operation(summary = "Listar citas de un negocio, con paginación y filtros opcionales",
            description = "Ejemplo: /api/appointments?businessId=1&from=2026-08-01T00:00:00Z&to=2026-08-02T00:00:00Z")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Listado obtenido correctamente"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "businessId es obligatorio, o rango de fechas inválido",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<Page<AppointmentResponse>> findAll(
            @Parameter(description = "Id del negocio (obligatorio)")
            @RequestParam(required = false) Long businessId,
            @Parameter(description = "Id del empleado (opcional)")
            @RequestParam(required = false) Long employeeId,
            @Parameter(description = "Id del cliente (opcional)")
            @RequestParam(required = false) Long customerId,
            @Parameter(description = "Estado de la cita (opcional). Valores: PENDING, CONFIRMED, CANCELLED, "
                    + "COMPLETED, NO_SHOW")
            @RequestParam(required = false) AppointmentStatus status,
            @Parameter(description = "Instant UTC desde el cual filtrar por startAt (opcional), "
                    + "ejemplo: 2026-08-01T00:00:00Z")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "Instant UTC hasta el cual filtrar por startAt (opcional), "
                    + "ejemplo: 2026-08-02T00:00:00Z")
            @RequestParam(required = false) Instant to,
            @PageableDefault(sort = "startAt", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<AppointmentResponse> page = appointmentService.findAll(
                businessId, employeeId, customerId, status, from, to, pageable);
        return ResponseFactory.success(page);
    }

    @PutMapping("/{id}/reschedule")
    @Operation(summary = "Reprogramar una cita existente",
            description = "Solo permite cambiar startAt; endAt se recalcula automáticamente. "
                    + "Vuelve a validar horario laboral y superposición.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Cita reprogramada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Datos inválidos, businessId faltante o fecha en el pasado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Cita no encontrada en ese negocio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Cita cancelada, fuera de horario, o superpuesta",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<AppointmentResponse> reschedule(
            @PathVariable Long id,
            @Parameter(description = "Id del negocio (obligatorio)")
            @RequestParam(required = false) Long businessId,
            @Valid @RequestBody RescheduleAppointmentRequest request) {
        AppointmentResponse updated = appointmentService.reschedule(id, businessId, request);
        return ResponseFactory.success("Cita reprogramada exitosamente", updated);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Actualizar el estado de una cita",
            description = "Valores permitidos: CONFIRMED, CANCELLED, COMPLETED, NO_SHOW. "
                    + "No se puede establecer PENDING manualmente ni modificar una cita ya CANCELLED.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Estado actualizado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Datos inválidos o businessId faltante",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Cita no encontrada en ese negocio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Transición de estado inválida",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<AppointmentResponse> updateStatus(
            @PathVariable Long id,
            @Parameter(description = "Id del negocio (obligatorio)")
            @RequestParam(required = false) Long businessId,
            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        AppointmentResponse updated = appointmentService.updateStatus(id, businessId, request);
        return ResponseFactory.success("Estado de la cita actualizado exitosamente", updated);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancelar una cita",
            description = "Cancelación lógica (status = CANCELLED). Operación idempotente: "
                    + "si la cita ya está cancelada, devuelve la cita sin error.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Cita cancelada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "businessId es obligatorio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Cita no encontrada en ese negocio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<AppointmentResponse> cancel(
            @PathVariable Long id,
            @Parameter(description = "Id del negocio (obligatorio)")
            @RequestParam(required = false) Long businessId) {
        AppointmentResponse cancelled = appointmentService.cancel(id, businessId);
        return ResponseFactory.success("Cita cancelada exitosamente", cancelled);
    }

}
