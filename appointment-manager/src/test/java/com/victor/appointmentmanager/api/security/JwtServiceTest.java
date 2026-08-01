package com.victor.appointmentmanager.api.security;

import com.victor.appointmentmanager.api.modules.users.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-0123456789";

    private final JwtService jwtService = new JwtService(SECRET, 3_600_000L);

    @Test
    void generatesAndValidatesAToken() {
        AuthenticatedUser user = new AuthenticatedUser(42L, "ana@example.com", "hashed",
                UserRole.OWNER, 9L, true);

        String token = jwtService.generateToken(user);

        Optional<Long> userId = jwtService.extractUserId(token);

        assertThat(userId).contains(42L);
    }

    @Test
    void extractsClaims() {
        AuthenticatedUser user = new AuthenticatedUser(42L, "ana@example.com", "hashed",
                UserRole.OWNER, 9L, true);

        String token = jwtService.generateToken(user);

        Optional<Claims> claims = jwtService.parseClaims(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("42");
        assertThat(claims.get().get("email")).isEqualTo("ana@example.com");
        assertThat(claims.get().get("businessId", Integer.class)).isEqualTo(9);
        assertThat(claims.get().get("role")).isEqualTo("OWNER");
    }

    @Test
    void rejectsExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(3600);

        String expiredToken = Jwts.builder()
                .subject("42")
                .issuedAt(Date.from(past.minusSeconds(60)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        Optional<Long> userId = jwtService.extractUserId(expiredToken);

        assertThat(userId).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithADifferentKey() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-completely-different-secret-key-0123456789".getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        String tokenSignedWithOtherKey = Jwts.builder()
                .subject("42")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(otherKey)
                .compact();

        Optional<Long> userId = jwtService.extractUserId(tokenSignedWithOtherKey);

        assertThat(userId).isEmpty();
    }

}
