package com.victor.appointmentmanager.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class AppAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        // TEMP (MVP3 Fase 3 - diagnóstico bug 401 en POST /messages): confirma en logs que este
        // 401 realmente proviene de Spring Security (y no de una excepción interna mal mapeada).
        // No loggea token ni secretos. Remover una vez confirmada la causa en producción.
        log.debug("AuthenticationEntryPoint 401: method={} path={} reason={}",
                request.getMethod(), request.getRequestURI(), authException.getClass().getSimpleName());
        SecurityErrorResponseWriter.write(response, request, objectMapper,
                HttpStatus.UNAUTHORIZED, "No autenticado. El token es ausente, inválido o expiró.");
    }

}
