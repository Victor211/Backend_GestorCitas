package com.victor.appointmentmanager.api.modules.auth.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.modules.auth.dto.request.LoginRequest;
import com.victor.appointmentmanager.api.modules.auth.dto.request.RegisterRequest;
import com.victor.appointmentmanager.api.modules.auth.dto.response.AuthResponse;
import com.victor.appointmentmanager.api.modules.auth.dto.response.CurrentUserResponse;
import com.victor.appointmentmanager.api.modules.users.entity.User;
import com.victor.appointmentmanager.api.modules.users.enums.UserRole;
import com.victor.appointmentmanager.api.modules.users.repository.UserRepository;
import com.victor.appointmentmanager.api.security.AuthenticatedUser;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.security.JwtService;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    private Business business;
    private User user;
    private AuthenticatedUser authenticatedUser;

    @BeforeEach
    void setUp() {
        business = new Business();
        business.setId(1L);
        business.setName("Barbería Central");

        user = new User();
        user.setId(7L);
        user.setFirstName("Carlos");
        user.setLastName("Ruiz");
        user.setEmail("carlos@example.com");
        user.setPassword("{bcrypt}hashed");
        user.setRole(UserRole.OWNER);
        user.setBusiness(business);

        authenticatedUser = new AuthenticatedUser(7L, "carlos@example.com", "{bcrypt}hashed",
                UserRole.OWNER, 1L, true);
    }

    private RegisterRequest buildRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setOwnerFirstName("Carlos");
        request.setOwnerLastName("Ruiz");
        request.setEmail("Carlos@Example.com");
        request.setPassword("supersecret123");
        request.setBusinessName("Barbería Central");
        request.setBusinessPhone("+595981000000");
        request.setBusinessTimezone("America/Asuncion");
        return request;
    }

    @Test
    void registersSuccessfully() {
        RegisterRequest request = buildRegisterRequest();

        when(userRepository.existsByEmailIgnoreCase("carlos@example.com")).thenReturn(false);
        when(businessRepository.save(any(Business.class))).thenReturn(business);
        when(passwordEncoder.encode("supersecret123")).thenReturn("{bcrypt}hashed");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateToken(any(AuthenticatedUser.class))).thenReturn("jwt-token");
        when(jwtService.getExpirationMillis()).thenReturn(3600000L);

        AuthResponse response = authService.register(request);

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUser().getEmail()).isEqualTo("carlos@example.com");
        assertThat(response.getUser().getBusinessId()).isEqualTo(1L);
    }

    @Test
    void throwsWhenEmailAlreadyRegistered() {
        RegisterRequest request = buildRegisterRequest();
        when(userRepository.existsByEmailIgnoreCase("carlos@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class);

        verify(businessRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void throwsBadRequestWhenTimezoneIsInvalid() {
        RegisterRequest request = buildRegisterRequest();
        request.setBusinessTimezone("Not/AZone");
        when(userRepository.existsByEmailIgnoreCase("carlos@example.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ResponseStatusException.class);

        verify(businessRepository, never()).save(any());
    }

    @Test
    void encodesPasswordBeforeSaving() {
        RegisterRequest request = buildRegisterRequest();

        when(userRepository.existsByEmailIgnoreCase("carlos@example.com")).thenReturn(false);
        when(businessRepository.save(any(Business.class))).thenReturn(business);
        when(passwordEncoder.encode("supersecret123")).thenReturn("{bcrypt}hashed");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateToken(any(AuthenticatedUser.class))).thenReturn("jwt-token");
        when(jwtService.getExpirationMillis()).thenReturn(3600000L);

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("{bcrypt}hashed");
        assertThat(userCaptor.getValue().getPassword()).isNotEqualTo("supersecret123");
        verify(passwordEncoder).encode("supersecret123");
    }

    @Test
    void createsBusinessAndUserTogetherAndLinksThem() {
        RegisterRequest request = buildRegisterRequest();

        when(userRepository.existsByEmailIgnoreCase("carlos@example.com")).thenReturn(false);
        when(businessRepository.save(any(Business.class))).thenReturn(business);
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}hashed");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateToken(any(AuthenticatedUser.class))).thenReturn("jwt-token");
        when(jwtService.getExpirationMillis()).thenReturn(3600000L);

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getBusiness()).isEqualTo(business);
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.OWNER);
        verify(businessRepository).save(any(Business.class));
    }

    @Test
    void logsInSuccessfully() {
        LoginRequest request = new LoginRequest();
        request.setEmail("Carlos@Example.com");
        request.setPassword("supersecret123");

        Authentication authentication = new TestingAuthenticationToken(authenticatedUser, null);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(authenticatedUser)).thenReturn("jwt-token");
        when(jwtService.getExpirationMillis()).thenReturn(3600000L);

        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getUser().getEmail()).isEqualTo("carlos@example.com");
    }

    @Test
    void throwsUnauthorizedOnInvalidCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("carlos@example.com");
        request.setPassword("wrong-password");

        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void throwsUnauthorizedWhenUserIsInactive() {
        LoginRequest request = new LoginRequest();
        request.setEmail("carlos@example.com");
        request.setPassword("supersecret123");

        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void throwsForbiddenWhenBusinessIsInactive() {
        LoginRequest request = new LoginRequest();
        request.setEmail("carlos@example.com");
        request.setPassword("supersecret123");

        Business inactiveBusiness = new Business();
        inactiveBusiness.setId(1L);
        inactiveBusiness.setActive(false);

        User userWithInactiveBusiness = new User();
        userWithInactiveBusiness.setId(7L);
        userWithInactiveBusiness.setEmail("carlos@example.com");
        userWithInactiveBusiness.setRole(UserRole.OWNER);
        userWithInactiveBusiness.setBusiness(inactiveBusiness);

        Authentication authentication = new TestingAuthenticationToken(authenticatedUser, null);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(userWithInactiveBusiness));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void generatesJwtTokenOnLogin() {
        LoginRequest request = new LoginRequest();
        request.setEmail("carlos@example.com");
        request.setPassword("supersecret123");

        Authentication authentication = new TestingAuthenticationToken(authenticatedUser, null);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(authenticatedUser)).thenReturn("jwt-token");
        when(jwtService.getExpirationMillis()).thenReturn(3600000L);

        authService.login(request);

        verify(jwtService).generateToken(authenticatedUser);
    }

    @Test
    void returnsCurrentUser() {
        when(currentUserProvider.getCurrentUser()).thenReturn(authenticatedUser);
        when(userRepository.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(user));

        CurrentUserResponse response = authService.getCurrentUser();

        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getEmail()).isEqualTo("carlos@example.com");
        assertThat(response.getBusinessId()).isEqualTo(1L);
        assertThat(response.getBusinessName()).isEqualTo("Barbería Central");
    }

}
