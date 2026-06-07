package com.circleguard.auth

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.*
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = [
    "jwt.secret=my-super-secret-dev-key-32-chars-long-12345678",
    "jwt.expiration=3600000",
    "qr.secret=my-qr-secret-key-for-dev-1234567890",
    "qr.expiration=300000"
])
@Testcontainers
class AuthIntegrationTest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            withDatabaseName("circleguard_auth")
            withUsername("admin")
            withPassword("password")
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    @BeforeEach
    fun setUp() {
        // Wait for database and migrations to run
        Thread.sleep(2000)
    }

    @Test
    fun `login with valid credentials returns JWT token`() {
        val body = """{"username":"staff_guard","password":"password"}""".toByteArray()
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = restTemplate.exchange(
            "/api/v1/auth/login",
            HttpMethod.POST,
            HttpEntity(body, headers),
            Map::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body?.get("token"))
        assertEquals("Bearer", response.body?.get("type"))
        assertNotNull(response.body?.get("anonymousId"))
    }

    @Test
    fun `login with invalid credentials returns 401 or HTTP error`() {
        val requestBody = mapOf("username" to "staff_guard", "password" to "wrongpassword")
        try {
            val response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                requestBody,
                Map::class.java
            )
            // If we get here, expect 401
            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        } catch (e: Exception) {
            // ResourceAccessException or other HTTP errors are expected for invalid credentials
            assertTrue(e.message?.contains("401") ?: false || e.message?.contains("authentication") ?: false)
        }
    }

    @Test
    fun `login with different user returns token with correct anonymousId`() {
        val body = """{"username":"health_user","password":"password"}""".toByteArray()
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = restTemplate.exchange(
            "/api/v1/auth/login",
            HttpMethod.POST,
            HttpEntity(body, headers),
            Map::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val token = response.body?.get("token") as String
        val anonymousId = response.body?.get("anonymousId") as String

        // Verify JWT structure
        assertEquals(3, token.split(".").size)

        // Verify anonymousId is a valid UUID
        assertDoesNotThrow { UUID.fromString(anonymousId) }
    }

    @Test
    fun `visitor handoff generates token with VISITOR role`() {
        val anonymousId = UUID.randomUUID().toString()
        val body = """{"anonymousId":"$anonymousId"}""".toByteArray()
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = restTemplate.exchange(
            "/api/v1/auth/visitor/handoff",
            HttpMethod.POST,
            HttpEntity(body, headers),
            Map::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body?.get("token"))
        assertNotNull(response.body?.get("handoffPayload"))

        val handoff = response.body?.get("handoffPayload") as String
        assertTrue(handoff.startsWith("HANDOFF_TOKEN:"))
    }

    @Test
    fun `visitor handoff with invalid anonymousId returns 400`() {
        val body = """{"anonymousId":"not-a-uuid"}""".toByteArray()
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = restTemplate.exchange(
            "/api/v1/auth/visitor/handoff",
            HttpMethod.POST,
            HttpEntity(body, headers),
            Map::class.java
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertNotNull(response.body?.get("message"))
    }

    @Test
    fun `get users by permission returns list of users with that permission`() {
        val response = restTemplate.exchange(
            "/api/v1/users/permissions/identity:lookup",
            HttpMethod.GET,
            null,
            List::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue((response.body as List<*>).size >= 1)

        // health_user and super_admin should have identity:lookup permission
        val usernames = (response.body as List<Map<String, String>>).map { it["username"] }
        assertTrue(usernames.contains("health_user") || usernames.contains("super_admin"))
    }
}