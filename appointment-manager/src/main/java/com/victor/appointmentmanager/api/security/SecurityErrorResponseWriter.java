package com.victor.appointmentmanager.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victor.appointmentmanager.api.common.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Escribe respuestas de error con el mismo formato de ApiError usado por GlobalExceptionHandler.
 * Necesario porque los filtros de seguridad se ejecutan fuera del dispatch de Spring MVC,
 * por lo que @RestControllerAdvice nunca los intercepta.
 */
final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
    }

    static void write(HttpServletResponse response, HttpServletRequest request, ObjectMapper objectMapper,
                       HttpStatus status, String message) throws IOException {
        ApiError apiError = ApiError.builder()
                .success(false)
                .message(message)
                .status(status.value())
                .path(request.getRequestURI())
                .build();

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), apiError);
    }

}
