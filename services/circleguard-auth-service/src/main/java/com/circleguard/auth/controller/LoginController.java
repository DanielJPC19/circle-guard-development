package com.circleguard.auth.controller;

import com.circleguard.auth.service.JwtTokenService;
import com.circleguard.auth.client.IdentityClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class LoginController {
    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    private final AuthenticationManager authManager;
    private final JwtTokenService jwtService;
    private final IdentityClient identityClient;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        log.info("Login attempt for user: {}", username);

        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            log.info("Authentication successful for: {}", username);

            UUID anonymousId;
            try {
                anonymousId = identityClient.getAnonymousId(username);
                log.info("Anonymous ID retrieved: {}", anonymousId);
            } catch (Exception e) {
                log.error("Failed to get anonymous ID for user: {}", username, e);
                anonymousId = UUID.randomUUID();
                log.warn("Using fallback random UUID for user: {}", username);
            }

            String token = jwtService.generateToken(anonymousId, auth);

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "type", "Bearer",
                    "anonymousId", anonymousId.toString()
            ));
        } catch (BadCredentialsException e) {
            log.warn("Authentication failed for user {}: Bad credentials", username);
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        } catch (DisabledException e) {
            log.warn("Authentication failed for user {}: Account disabled", username);
            return ResponseEntity.status(403).body(Map.of("message", "Account is disabled"));
        } catch (LockedException e) {
            log.warn("Authentication failed for user {}: Account locked", username);
            return ResponseEntity.status(403).body(Map.of("message", "Account is locked"));
        } catch (AuthenticationServiceException e) {
            log.error("Authentication service error for user {}", username, e);
            return ResponseEntity.status(503).body(Map.of("message", "Authentication service temporarily unavailable"));
        } catch (Exception e) {
            log.error("Unexpected error during login for user {}", username, e);
            return ResponseEntity.status(500).body(Map.of("message", "Internal server error"));
        }
    }

    @PostMapping("/visitor/handoff")
    public ResponseEntity<Map<String, String>> generateVisitorHandoff(@RequestBody Map<String, String> request) {
        String anonymousIdStr = request.get("anonymousId");
        if (anonymousIdStr == null || anonymousIdStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "anonymousId is required"));
        }

        try {
            UUID anonymousId = UUID.fromString(anonymousIdStr);

            Authentication visitorAuth = new UsernamePasswordAuthenticationToken(
                    anonymousIdStr,
                    null,
                    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("VISITOR"))
            );

            String token = jwtService.generateToken(anonymousId, visitorAuth);

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "handoffPayload", "HANDOFF_TOKEN:" + anonymousId.toString() + ":" + token
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid anonymousId format: {}", anonymousIdStr);
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid anonymousId format"));
        } catch (Exception e) {
            log.error("Error generating visitor handoff", e);
            return ResponseEntity.status(500).body(Map.of("message", "Internal server error"));
        }
    }
}