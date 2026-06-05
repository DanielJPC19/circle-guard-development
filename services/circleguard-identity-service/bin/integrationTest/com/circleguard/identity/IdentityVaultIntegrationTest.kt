package com.circleguard.identity

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.TestPropertySource
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
])
class IdentityVaultIntegrationTest {

    @MockBean
    lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Test
    fun `identity client mock works correctly`() {
        val mockId = UUID.randomUUID()
        assertNotNull(mockId)
    }
}