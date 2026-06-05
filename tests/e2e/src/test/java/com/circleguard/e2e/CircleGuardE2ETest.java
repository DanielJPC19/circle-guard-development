package com.circleguard.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CircleGuardE2ETest {

    // Per-service URLs — each service runs on its own port per docker-compose.test.yml
    private static final String AUTH_URL      = System.getProperty("e2e.auth.url",      "http://localhost:8180");
    private static final String GATEWAY_URL   = System.getProperty("e2e.gateway.url",   "http://localhost:8087");
    private static final String PROMOTION_URL = System.getProperty("e2e.promotion.url", "http://localhost:8088");
    private static final String FORM_URL      = System.getProperty("e2e.form.url",      "http://localhost:8086");

    private static String staffToken       = "";
    private static String staffAnonymousId = "";
    private static String officialToken    = "";
    private static String officialAnonymousId = "";
    private static String lastQrToken      = "";

    @BeforeAll
    static void checkServicesAvailable() {
        try {
            var uri = java.net.URI.create(AUTH_URL);
            int port = uri.getPort() == -1 ? 80 : uri.getPort();
            try (var s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(uri.getHost(), port), 3000);
            }
        } catch (Exception e) {
            Assumptions.assumeTrue(false,
                "Auth service unreachable at " + AUTH_URL + " — start docker-compose.test.yml first");
        }
    }

    // ── Test 1: Auth flow + QR generation ───────────────────────────────────
    @Test
    @Order(1)
    void authFlowLoginReturnsJwtAndQrGenerateAcceptsIt() {
        var loginResp = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"staff_guard\",\"password\":\"password\"}")
                .post(AUTH_URL + "/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        staffToken       = loginResp.getString("token");
        staffAnonymousId = loginResp.getString("anonymousId");

        assertNotNull(staffToken, "token must not be null");
        assertFalse(staffToken.isEmpty());
        assertEquals(3, staffToken.split("\\.").length,
                "JWT must have header.payload.signature separated by '.'");
        assertEquals("Bearer", loginResp.getString("type"));

        // Verify JWT is accepted by the auth-service QR-generate endpoint
        lastQrToken = RestAssured.given()
                .header("Authorization", "Bearer " + staffToken)
                .get(AUTH_URL + "/api/v1/auth/qr/generate")
                .then()
                .statusCode(200)
                .extract().jsonPath().getString("qrToken");

        assertNotNull(lastQrToken, "qrToken must be returned by /api/v1/auth/qr/generate");
        assertFalse(lastQrToken.isEmpty());
    }

    // ── Test 2: Gate check-in with valid QR returns GREEN ───────────────────
    @Test
    @Order(2)
    void gatewayCheckinWithValidQrReturnsGreen() {
        if (staffToken.isEmpty()) {
            var r = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body("{\"username\":\"staff_guard\",\"password\":\"password\"}")
                    .post(AUTH_URL + "/api/v1/auth/login")
                    .then().statusCode(200).extract().jsonPath();
            staffToken = r.getString("token");
        }
        if (lastQrToken.isEmpty()) {
            lastQrToken = RestAssured.given()
                    .header("Authorization", "Bearer " + staffToken)
                    .get(AUTH_URL + "/api/v1/auth/qr/generate")
                    .then().statusCode(200)
                    .extract().jsonPath().getString("qrToken");
        }

        var result = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"token\":\"" + lastQrToken + "\"}")
                .post(GATEWAY_URL + "/api/v1/gate/validate")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertTrue(result.getBoolean("valid"), "A valid QR token must produce valid=true");
        assertEquals("GREEN", result.getString("status"), "A valid QR must get GREEN status");
    }

    // ── Test 3: Gate check-in with invalid QR returns RED ───────────────────
    @Test
    @Order(3)
    void gatewayCheckinWithInvalidQrReturnsRed() {
        var result = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"token\":\"totally-invalid-qr-token-xyz-12345\"}")
                .post(GATEWAY_URL + "/api/v1/gate/validate")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertFalse(result.getBoolean("valid"), "An invalid QR token must produce valid=false");
        assertEquals("RED", result.getString("status"), "An invalid QR must get RED status");
    }

    // ── Test 4: Promotion report and status query ────────────────────────────
    // Note: graph-based propagation (SUSPECT spread) requires Neo4j nodes to pre-exist
    // via /api/v1/encounters/report. This test verifies the reporting API and stats endpoint.
    @Test
    @Order(4)
    void promotionReportAcceptsRequestAndStatsEndpointResponds() {
        var loginResp = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"health_user\",\"password\":\"password\"}")
                .post(AUTH_URL + "/api/v1/auth/login")
                .then().statusCode(200)
                .extract().jsonPath();

        officialToken       = loginResp.getString("token");
        officialAnonymousId = loginResp.getString("anonymousId");

        // Report health status for this user
        var reportResp = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"anonymousId\":\"" + officialAnonymousId + "\",\"status\":\"CONFIRMED\"}")
                .post(PROMOTION_URL + "/api/v1/promotion/report")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertTrue(reportResp.getBoolean("success"), "report should return success=true");
        assertEquals("CONFIRMED", reportResp.getString("status"));
        assertEquals(officialAnonymousId, reportResp.getString("anonymousId"));

        // Query status — response is always 200; status may be "UNKNOWN" if no Neo4j node exists
        var statusResp = RestAssured.given()
                .get(PROMOTION_URL + "/api/v1/promotion/status/" + officialAnonymousId)
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertNotNull(statusResp.getString("status"),
                "status field must always be present in the response");

        // Verify aggregated health stats endpoint
        var stats = RestAssured.given()
                .get(PROMOTION_URL + "/api/v1/health-status/stats")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertNotNull(stats.get("totalUsers"),
                "health-status/stats must return a totalUsers field");
    }

    // ── Test 5: Health form submit and persistence ───────────────────────────
    @Test
    @Order(5)
    @SuppressWarnings("unchecked")
    void healthFormSubmitReturnsPersistedSurveyWithCorrectFields() {
        String surveyAnonymousId = UUID.randomUUID().toString();
        String today = LocalDate.now().toString();

        // form-service has no JWT requirement
        var submitResp = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"anonymousId\":\"" + surveyAnonymousId + "\","
                        + "\"hasFever\":true,"
                        + "\"hasCough\":true,"
                        + "\"exposureDate\":\"" + today + "\"}")
                .post(FORM_URL + "/api/v1/surveys")
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertNotNull(submitResp.getString("id"), "Submitted survey must return a persisted id");
        assertTrue(submitResp.getBoolean("hasFever"), "hasFever must match the submitted value");
        assertTrue(submitResp.getBoolean("hasCough"), "hasCough must match the submitted value");

        // Verify the pending certificates endpoint is reachable
        // (only surveys with attachments appear here; this survey has no attachment)
        List<Object> pending = RestAssured.given()
                .get(FORM_URL + "/api/v1/certificates/pending")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("$");

        assertNotNull(pending, "Pending certificates endpoint must return a valid JSON array");
    }
}
