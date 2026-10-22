package com.victor.appointmentmanager.api.modules.users.controller;

import com.victor.appointmentmanager.api.common.exception.ApiError;
import com.victor.appointmentmanager.api.common.response.ApiResponse;
import com.victor.appointmentmanager.api.common.response.ResponseFactory;
import com.victor.appointmentmanager.api.modules.users.dto.request.CreateUserRequest;
import com.victor.appointmentmanager.api.modules.users.dto.request.UpdateUserStatusRequest;
import com.victor.appointmentmanager.api.modules.users.dto.response.UserResponse;
import com.victor.appointmentmanager.api.modules.users.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
@Tag(name = "Users", description = "Administración de los usuarios del negocio autenticado (solo OWNER)")
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un usuario ADMIN o STAFF en el negocio del OWNER autenticado",
            description = "El negocio se toma del JWT; no se acepta businessId. No permite crear OWNER.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "Usuario creado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Datos inválidos o rol no permitido",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "El usuario autenticado no es OWNER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Ya existe una cuenta con ese email",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseFactory.created("Usuario creado exitosamente", userService.create(request));
    }

    @GetMapping
    @Operation(summary = "Listar los usuarios del negocio autenticado",
            description = "Paginado (page, size; máximo 100). El orden es fijo (nombre, apellido, id); "
                    + "el parámetro sort se ignora.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Listado obtenido correctamente"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "El usuario autenticado no es OWNER",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<Page<UserResponse>> findAll(Pageable pageable) {
        return ResponseFactory.success(userService.findAll(pageable));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activar o desactivar un usuario del negocio",
            description = "No se puede desactivar la propia cuenta ni al último OWNER activo. "
                    + "Un usuario inactivo no puede iniciar sesión y su JWT deja de ser válido de inmediato.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Estado actualizado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "El usuario autenticado no es OWNER",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Usuario no encontrado en este negocio",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Operación no permitida (cuenta propia o último OWNER)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<UserResponse> updateStatus(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseFactory.success("Estado del usuario actualizado exitosamente",
                userService.updateStatus(id, request.getActive()));
    }

}
