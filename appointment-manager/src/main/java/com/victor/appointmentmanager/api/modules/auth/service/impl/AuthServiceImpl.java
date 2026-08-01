package com.victor.appointmentmanager.api.modules.auth.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.modules.auth.dto.request.LoginRequest;
import com.victor.appointmentmanager.api.modules.auth.dto.request.RegisterRequest;
import com.victor.appointmentmanager.api.modules.auth.dto.response.AuthResponse;
import com.victor.appointmentmanager.api.modules.auth.dto.response.CurrentUserResponse;
import com.victor.appointmentmanager.api.modules.auth.service.AuthService;
import com.victor.appointmentmanager.api.modules.users.entity.User;
import com.victor.appointmentmanager.api.modules.users.enums.UserRole;
import com.victor.appointmentmanager.api.modules.users.repository.UserRepository;
import com.victor.appointmentmanager.api.security.AppUserDetailsService;
import com.victor.appointmentmanager.api.security.AuthenticatedUser;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.security.JwtService;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Credenciales inválidas";

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessException("Ya existe una cuenta registrada con ese email");
        }

        ZoneId zoneId = parseZoneIdOrThrow(request.getBusinessTimezone());

        Business business = new Business();
        business.setName(request.getBusinessName());
        business.setPhone(request.getBusinessPhone());
        business.setEmail(request.getBusinessEmail());
        business.setAddress(request.getBusinessAddress());
        business.setTimezone(zoneId.getId());
        Business savedBusiness = businessRepository.save(business);

        User user = new User();
        user.setFirstName(request.getOwnerFirstName());
        user.setLastName(request.getOwnerLastName());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.OWNER);
        user.setBusiness(savedBusiness);
        User savedUser = userRepository.save(user);

        AuthenticatedUser principal = AppUserDetailsService.toAuthenticatedUser(savedUser);
        String token = jwtService.generateToken(principal);

        return buildAuthResponse(token, savedUser, savedBusiness);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        Authentication authentication = authenticate(normalizedEmail, request.getPassword());
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();

        User user = userRepository.findByIdAndActiveTrue(principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE));

        if (!Boolean.TRUE.equals(user.getBusiness().getActive())) {
            throw new AccessDeniedException("El negocio asociado a este usuario está inactivo");
        }

        String token = jwtService.generateToken(principal);
        return buildAuthResponse(token, user, user.getBusiness());
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser() {
        AuthenticatedUser principal = currentUserProvider.getCurrentUser();

        User user = userRepository.findByIdAndActiveTrue(principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE));

        return toCurrentUserResponse(user, user.getBusiness());
    }

    private Authentication authenticate(String email, String rawPassword) {
        try {
            return authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, rawPassword));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }
    }

    private ZoneId parseZoneIdOrThrow(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La zona horaria indicada no es válida");
        }
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT).trim();
    }

    private AuthResponse buildAuthResponse(String token, User user, Business business) {
        AuthResponse response = new AuthResponse();
        response.setAccessToken(token);
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtService.getExpirationMillis());
        response.setUser(toCurrentUserResponse(user, business));
        return response;
    }

    private CurrentUserResponse toCurrentUserResponse(User user, Business business) {
        CurrentUserResponse response = new CurrentUserResponse();
        response.setId(user.getId());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setBusinessId(business.getId());
        response.setBusinessName(business.getName());
        return response;
    }

}
