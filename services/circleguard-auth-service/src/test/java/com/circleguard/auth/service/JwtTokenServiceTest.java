package com.circleguard.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {

    private JwtTokenService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtTokenService(
                "my-super-secret-test-key-32-chars-long",
                3600000L
        );
    }

    @Test
    void generateToken_producesNonNullTokenWithThreeParts() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user123", null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        String token = jwtService.generateToken(UUID.randomUUID(), auth);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertEquals(3, token.split("\\.").length, "JWT must have header.payload.signature");
    }

    @Test
    void generateToken_withShortExpiration_stillProducesValidStructure() throws InterruptedException {
        JwtTokenService shortLived = new JwtTokenService(
                "my-super-secret-test-key-32-chars-long",
                1L
        );
        Authentication auth = new UsernamePasswordAuthenticationToken("user123", null, List.of());

        String token = shortLived.generateToken(UUID.randomUUID(), auth);
        Thread.sleep(10);

        assertNotNull(token);
        assertEquals(3, token.split("\\.").length, "JWT must have header.payload.signature");
    }
}
