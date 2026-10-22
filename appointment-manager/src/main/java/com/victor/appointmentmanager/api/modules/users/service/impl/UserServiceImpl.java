package com.victor.appointmentmanager.api.modules.users.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;
import com.victor.appointmentmanager.api.common.util.StringNormalizer;
import com.victor.appointmentmanager.api.modules.users.dto.request.CreateUserRequest;
import com.victor.appointmentmanager.api.modules.users.dto.response.UserResponse;
import com.victor.appointmentmanager.api.modules.users.entity.User;
import com.victor.appointmentmanager.api.modules.users.enums.UserRole;
import com.victor.appointmentmanager.api.modules.users.mapper.UserMapper;
import com.victor.appointmentmanager.api.modules.users.repository.UserRepository;
import com.victor.appointmentmanager.api.modules.users.service.UserService;
import com.victor.appointmentmanager.api.security.AuthenticatedUser;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Set<UserRole> CREATABLE_ROLES = Set.of(UserRole.ADMIN, UserRole.STAFF);
    private static final int MAX_PAGE_SIZE = 100;
    // El orden es fijo: aceptar un sort del cliente permitiría ordenar por password (fuga por inferencia).
    private static final Sort FIXED_SORT = Sort.by("firstName", "lastName", "id");
    private static final String DUPLICATE_EMAIL_MESSAGE = "Ya existe una cuenta registrada con ese email";

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        AuthenticatedUser currentUser = requireOwner();

        if (!CREATABLE_ROLES.contains(request.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se pueden crear usuarios con rol ADMIN o STAFF");
        }

        String normalizedEmail = StringNormalizer.normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessException(DUPLICATE_EMAIL_MESSAGE);
        }

        User user = new User();
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setBusiness(businessRepository.getReferenceById(currentUser.getBusinessId()));
        user.setActive(true);

        try {
            return userMapper.toDto(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            // Solicitud concurrente con el mismo email: la restricción única de la BD es la garantía final.
            throw new BusinessException(DUPLICATE_EMAIL_MESSAGE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(Pageable pageable) {
        AuthenticatedUser currentUser = requireOwner();
        Pageable safePageable = PageRequest.of(
                pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), FIXED_SORT);
        return userRepository.findAllByBusinessId(currentUser.getBusinessId(), safePageable)
                .map(userMapper::toDto);
    }

    @Override
    @Transactional
    public UserResponse updateStatus(Long id, boolean active) {
        AuthenticatedUser currentUser = requireOwner();

        // Un id de otro negocio es indistinguible de uno inexistente (404), para no revelar su existencia.
        User target = userRepository.findByIdAndBusinessId(id, currentUser.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!active && Boolean.TRUE.equals(target.getActive())) {
            if (target.getId().equals(currentUser.getUserId())) {
                throw new BusinessException("No puede desactivar su propia cuenta");
            }
            if (target.getRole() == UserRole.OWNER
                    && userRepository.countActiveByBusinessIdAndRole(
                            currentUser.getBusinessId(), UserRole.OWNER) <= 1) {
                throw new BusinessException("No se puede desactivar al último OWNER activo del negocio");
            }
        }

        target.setActive(active);
        return userMapper.toDto(userRepository.save(target));
    }

    // Defensa en profundidad: la regla ya se aplica en SecurityConfig y @PreAuthorize del controlador.
    private AuthenticatedUser requireOwner() {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        if (currentUser.getRole() != UserRole.OWNER) {
            throw new AccessDeniedException("Solo el OWNER puede administrar usuarios");
        }
        return currentUser;
    }

}
