package com.circleguard.auth.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class IdentityClient {
    private static final Logger log = LoggerFactory.getLogger(IdentityClient.class);
    private final RestTemplate restTemplate;

    private static final String IDENTITY_URL = "http://localhost:8083/api/v1/identities/map";

    public IdentityClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public UUID getAnonymousId(String realIdentity) {
        try {
            Map<String, String> request = Map.of("realIdentity", realIdentity);
            var response = restTemplate.postForEntity(IDENTITY_URL, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object anonymousIdObj = response.getBody().get("anonymousId");
                if (anonymousIdObj != null) {
                    return UUID.fromString(anonymousIdObj.toString());
                }
            }

            throw new RuntimeException("Invalid response from Identity Service: empty body or unexpected status");
        } catch (HttpStatusCodeException e) {
            log.error("Identity Service returned error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Identity Service error: " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            log.error("Failed to connect to Identity Service at {}", IDENTITY_URL, e);
            throw new RuntimeException("Identity Service unavailable", e);
        } catch (Exception e) {
            log.error("Unexpected error calling Identity Service", e);
            throw new RuntimeException("Failed to get anonymous ID", e);
        }
    }

    public boolean isIdentityServiceAvailable() {
        try {
            restTemplate.getForEntity("http://localhost:8083/actuator/health", String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}