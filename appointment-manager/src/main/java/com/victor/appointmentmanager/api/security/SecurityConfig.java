package com.victor.appointmentmanager.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/webhooks/whatsapp",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };

    // Módulos operativos: OWNER y ADMIN. STAFF no tiene ningún permiso operativo definido todavía
    // (decisión de producto pendiente), por lo que queda denegado en todos ellos.
    private static final String[] OPERATIONAL_ENDPOINTS = {
            "/api/appointments/**",
            "/api/customers/**",
            "/api/employees/**",
            "/api/schedules/**",
            "/api/services/**",
            "/api/dashboard/**",
            "/api/conversations/**",
            "/api/ai/**",
            "/api/settings/business"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AppAuthenticationEntryPoint authenticationEntryPoint;
    private final AppAccessDeniedHandler accessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(AppUserDetailsService userDetailsService,
                                                              PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/users/**").hasRole("OWNER")
                        // La configuración del negocio (nombre, zona horaria) solo la modifica el OWNER.
                        .requestMatchers(HttpMethod.PUT, "/api/settings/business").hasRole("OWNER")
                        .requestMatchers(OPERATIONAL_ENDPOINTS).hasAnyRole("OWNER", "ADMIN")
                        // Denegar por defecto: todo endpoint nuevo es solo OWNER hasta clasificarlo aquí.
                        .anyRequest().hasRole("OWNER"))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
