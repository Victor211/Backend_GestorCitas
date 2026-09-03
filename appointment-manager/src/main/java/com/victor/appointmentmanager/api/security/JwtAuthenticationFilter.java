package com.victor.appointmentmanager.api.security;

import com.victor.appointmentmanager.api.modules.users.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Si no hay token o es inválido, simplemente no establece Authentication y continúa la cadena:
 * las rutas públicas siguen funcionando, y las protegidas terminan siendo rechazadas más adelante
 * por el AuthenticationEntryPoint configurado en Spring Security (401).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean headerPresent = authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX);
        boolean authenticationResolved = false;

        if (headerPresent && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = authorizationHeader.substring(BEARER_PREFIX.length());

            authenticationResolved = jwtService.extractUserId(token)
                    .flatMap(userRepository::findByIdAndActiveTrue)
                    .map(user -> {
                        AuthenticatedUser principal = AppUserDetailsService.toAuthenticatedUser(user);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        return true;
                    })
                    .orElse(false);
        }

        // TEMP (MVP3 Fase 3 - diagnóstico bug 401 en POST /messages): nunca loggea el token ni el
        // secreto, solo método/ruta/booleans. Remover una vez confirmada la causa en producción.
        if (log.isDebugEnabled()) {
            log.debug("JWT filter: method={} path={} headerPresent={} authenticationResolved={}",
                    request.getMethod(), request.getRequestURI(), headerPresent, authenticationResolved);
        }

        filterChain.doFilter(request, response);
    }

}
