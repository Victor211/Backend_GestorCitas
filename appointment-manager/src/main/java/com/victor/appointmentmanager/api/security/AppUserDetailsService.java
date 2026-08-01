package com.victor.appointmentmanager.api.security;

import com.victor.appointmentmanager.api.modules.users.entity.User;
import com.victor.appointmentmanager.api.modules.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        return toAuthenticatedUser(user);
    }

    public static AuthenticatedUser toAuthenticatedUser(User user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getRole(),
                user.getBusiness().getId(),
                Boolean.TRUE.equals(user.getActive()));
    }

}
