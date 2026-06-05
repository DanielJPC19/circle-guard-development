package com.circleguard.promotion.controller;

import com.circleguard.promotion.service.HealthStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/promotion")
@RequiredArgsConstructor
@Slf4j
public class HealthStatusController {
    private final HealthStatusService statusService;

    @PostMapping("/report")
    public ResponseEntity<Map<String, Object>> reportStatus(@RequestBody Map<String, Object> request) {
        String anonymousId = request.get("anonymousId") != null ? request.get("anonymousId").toString() : null;
        String status = request.get("status") != null ? request.get("status").toString() : null;

        if (anonymousId == null || anonymousId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "anonymousId is required"));
        }
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required"));
        }

        boolean override = request.containsKey("adminOverride") && Boolean.TRUE.equals(request.get("adminOverride"));

        try {
            statusService.updateStatus(anonymousId, status, override);
            log.info("Status updated: {} -> {}", anonymousId, status);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "anonymousId", anonymousId,
                "status", status
            ));
        } catch (Exception e) {
            log.error("Error updating status for {}", anonymousId, e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to update status: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/confirmed")
    public ResponseEntity<Map<String, Object>> confirmPositive(@RequestBody Map<String, String> request) {
        String anonymousId = request.get("anonymousId");

        if (anonymousId == null || anonymousId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "anonymousId is required"));
        }

        try {
            statusService.updateStatus(anonymousId, "CONFIRMED");
            log.info("Confirmed positive case for: {}", anonymousId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "anonymousId", anonymousId,
                "status", "CONFIRMED"
            ));
        } catch (Exception e) {
            log.error("Error confirming case for {}", anonymousId, e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to confirm case: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/status/{anonymousId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String anonymousId) {
        try {
            String cachedStatus = statusService.getCachedStatus(anonymousId);
            return ResponseEntity.ok(Map.of(
                "anonymousId", anonymousId,
                "status", cachedStatus != null ? cachedStatus : "UNKNOWN"
            ));
        } catch (Exception e) {
            log.error("Error getting status for {}", anonymousId, e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get status: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/resolve")
    public ResponseEntity<Map<String, Object>> resolve(@RequestBody Map<String, Object> request) {
        String anonymousId = request.get("anonymousId") != null ? request.get("anonymousId").toString() : null;

        if (anonymousId == null || anonymousId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "anonymousId is required"));
        }

        boolean override = request.containsKey("adminOverride") && Boolean.TRUE.equals(request.get("adminOverride"));

        try {
            statusService.resolveStatus(anonymousId, override);
            log.info("Status resolved for: {}", anonymousId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "anonymousId", anonymousId,
                "status", "ACTIVE"
            ));
        } catch (Exception e) {
            log.error("Error resolving status for {}", anonymousId, e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to resolve status: " + e.getMessage()
            ));
        }
    }
}