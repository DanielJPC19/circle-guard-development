package com.circleguard.gateway

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.*
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = [
    "qr.secret=my-qr-secret-key-for-dev-1234567890"
])
@Testcontainers
class GatewayIntegrationTest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    companion object {
        private const val QR_SECRET = "my-qr-secret-key-for-dev-1234567890"

        @Container
        @JvmStatic
        val redis = GenericContainer<Nothing>("redis:7.2").apply {
            withExposedPorts(6379)
        }

        @JvmStatic
        @org.springframework.test.context.DynamicPropertySource
        fun configureProperties(registry: org.springframework.test.context.DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379).toString() }
        }
    }

    @BeforeEach
    fun setUp() {
        redisTemplate.opsForValue().set("user:status:test-active-user", "ACTIVE")
    }

    private fun buildQrToken(anonymousId: String): String {
        val key = Keys.hmacShaKeyFor(QR_SECRET.toByteArray())
        return Jwts.builder().setSubject(anonymousId).signWith(key).compact()
    }

    @Test
    fun `valid QR token for an ACTIVE user returns GREEN and valid=true`() {
        val anonymousId = "test-active-user"
        val token = buildQrToken(anonymousId)

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = restTemplate.exchange(
            "/api/v1/gate/validate",
            HttpMethod.POST,
            HttpEntity("""{"token":"$token"}""", headers),
            Map::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("GREEN", response.body?.get("status"))
        assertEquals(true, response.body?.get("valid"))
    }

    @Test
    fun `valid QR token for a CONTAGIED user returns RED and valid=false`() {
        val anonymousId = "test-contagied-user-" + UUID.randomUUID()
        redisTemplate.opsForValue().set("user:status:$anonymousId", "CONTAGIED")
        val token = buildQrToken(anonymousId)

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = restTemplate.exchange(
            "/api/v1/gate/validate",
            HttpMethod.POST,
            HttpEntity("""{"token":"$token"}""", headers),
            Map::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("RED", response.body?.get("status"))
        assertEquals(false, response.body?.get("valid"))
    }

    @Test
    fun `tampered or invalid token returns RED`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = restTemplate.exchange(
            "/api/v1/gate/validate",
            HttpMethod.POST,
            HttpEntity("""{"token":"not.a.real.token"}""", headers),
            Map::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("RED", response.body?.get("status"))
        assertEquals(false, response.body?.get("valid"))
    }
}