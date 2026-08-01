package com.victor.appointmentmanager.api.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    public AuthenticatedUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser)) {
            throw new AccessDeniedException("No hay un usuario autenticado en el contexto actual");
        }
        return authenticatedUser;
    }

    public Long getCurrentBusinessId() {
        return getCurrentUser().getBusinessId();
    }

}
