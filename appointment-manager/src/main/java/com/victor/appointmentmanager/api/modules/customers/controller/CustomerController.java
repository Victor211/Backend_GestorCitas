package com.victor.appointmentmanager.api.modules.customers.controller;

import com.victor.appointmentmanager.api.common.exception.ApiError;
import com.victor.appointmentmanager.api.common.response.ApiResponse;
import com.victor.appointmentmanager.api.common.response.ResponseFactory;
import com.victor.appointmentmanager.api.modules.customers.dto.request.CreateCustomerRequest;
import com.victor.appointmentmanager.api.modules.customers.dto.request.UpdateCustomerRequest;
import com.victor.appointmentmanager.api.modules.customers.dto.response.CustomerResponse;
import com.victor.appointmentmanager.api.modules.customers.service.CustomerService;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Gestión de los clientes de un negocio")
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un nuevo cliente")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "Cliente creado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Negocio no encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Ya existe un cliente con ese teléfono en el negocio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse created = customerService.create(request);
        return ResponseFactory.created("Cliente creado exitosamente", created);
    }

    @GetMapping
    @Operation(summary = "Listar clientes activos de un negocio, con paginación y búsqueda opcional por nombre/apellido")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Listado obtenido correctamente"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "businessId es obligatorio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<Page<CustomerResponse>> findAll(
            @Parameter(description = "Id del negocio (obligatorio)")
            @RequestParam(required = false) Long businessId,
            @Parameter(description = "Filtro parcial por nombre o apellido, ignorando mayúsculas/minúsculas")
            @RequestParam(required = false) String name,
            Pageable pageable) {
        Page<CustomerResponse> page = customerService.findAll(businessId, name, pageable);
        return ResponseFactory.success(page);
    }

    @GetMapping("/by-phone")
    @Operation(summary = "Buscar un cliente por teléfono dentro de un negocio")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Cliente encontrado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "businessId y phone son obligatorios",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Cliente no encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<CustomerResponse> findByPhone(
            @Parameter(description = "Id del negocio (obligatorio)")
            @RequestParam(required = false) Long businessId,
            @Parameter(description = "Teléfono exacto del cliente (obligatorio)")
            @RequestParam(required = false) String phone) {
        return ResponseFactory.success(customerService.findByPhone(businessId, phone));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un cliente por su id")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Cliente encontrado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Cliente no encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<CustomerResponse> findById(@PathVariable Long id) {
        return ResponseFactory.success(customerService.findById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un cliente existente")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Cliente actualizado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Cliente no encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Ya existe un cliente con ese teléfono en el negocio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<CustomerResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody UpdateCustomerRequest request) {
        CustomerResponse updated = customerService.update(id, request);
        return ResponseFactory.success("Cliente actualizado exitosamente", updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar (lógicamente) un cliente")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Cliente eliminado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Cliente no encontrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "Error interno del servidor",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseFactory.success("Cliente eliminado exitosamente", null);
    }

}
