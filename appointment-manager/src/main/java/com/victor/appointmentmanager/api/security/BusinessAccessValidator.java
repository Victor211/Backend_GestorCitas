package com.victor.appointmentmanager.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Compara el businessId solicitado contra el businessId del usuario autenticado.
 * Punto único, explícito y reutilizable de aislamiento por Business (sin AOP).
 */
@Component
@RequiredArgsConstructor
public class BusinessAccessValidator {

    private final CurrentUserProvider currentUserProvider;

    public void validate(Long requestedBusinessId) {
        Long authenticatedBusinessId = currentUserProvider.getCurrentBusinessId();
        if (requestedBusinessId == null || !requestedBusinessId.equals(authenticatedBusinessId)) {
            throw new AccessDeniedException("No tiene acceso a los recursos de este negocio");
        }
    }

}
