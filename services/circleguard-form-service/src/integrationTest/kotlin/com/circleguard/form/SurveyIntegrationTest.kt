package com.circleguard.form

import com.circleguard.form.model.HealthSurvey
import com.circleguard.form.model.ValidationStatus
import com.circleguard.form.repository.HealthSurveyRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.*
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = [
    "spring.kafka.bootstrap-servers=localhost:9092"
])
@Testcontainers
class SurveyIntegrationTest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var surveyRepository: HealthSurveyRepository

    @MockBean
    lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            withDatabaseName("circleguard_form")
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
        surveyRepository.deleteAll()
    }

    @Test
    fun `submit survey with symptoms saves to database and sends Kafka event`() {
        val anonymousId = UUID.randomUUID()
        val survey = HealthSurvey.builder()
            .anonymousId(anonymousId)
            .hasFever(true)
            .hasCough(false)
            .otherSymptoms("headache")
            .exposureDate(LocalDate.now().minusDays(1))
            .validationStatus(null)
            .build()

        val body = survey
        val response = restTemplate.postForEntity(
            "/api/v1/surveys",
            body,
            HealthSurvey::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertNotNull(response.body?.id)
        assertEquals(anonymousId, response.body?.anonymousId)

        // Verify survey was saved to database
        val saved = surveyRepository.findById(response.body?.id!!).orElse(null)
        assertNotNull(saved)
        assertEquals(anonymousId, saved.anonymousId)
        assertEquals(true, saved.hasFever)
    }

    @Test
    fun `submit survey without attachment does not set validation status`() {
        val anonymousId = UUID.randomUUID()
        val survey = HealthSurvey.builder()
            .anonymousId(anonymousId)
            .hasFever(false)
            .hasCough(true)
            .otherSymptoms(null)
            .exposureDate(LocalDate.now())
            .attachmentPath(null)
            .validationStatus(null)
            .build()

        val response = restTemplate.postForEntity(
            "/api/v1/surveys",
            survey,
            HealthSurvey::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val saved = surveyRepository.findById(response.body?.id!!).orElse(null)
        assertNotNull(saved)
        assertNull(saved.validationStatus)
    }

    @Test
    fun `submit survey with attachment sets validation status to PENDING`() {
        val anonymousId = UUID.randomUUID()
        val survey = HealthSurvey.builder()
            .anonymousId(anonymousId)
            .hasFever(true)
            .hasCough(true)
            .otherSymptoms("fever and cough")
            .exposureDate(LocalDate.now().minusDays(2))
            .attachmentPath("/attachments/cert123.pdf")
            .validationStatus(null)
            .build()

        val response = restTemplate.postForEntity(
            "/api/v1/surveys",
            survey,
            HealthSurvey::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val saved = surveyRepository.findById(response.body?.id!!).orElse(null)
        assertNotNull(saved)
        assertEquals(ValidationStatus.PENDING, saved.validationStatus)
    }

    @Test
    fun `submit survey sends event to Kafka with correct data`() {
        val anonymousId = UUID.randomUUID()
        val survey = HealthSurvey.builder()
            .anonymousId(anonymousId)
            .hasFever(true)
            .hasCough(false)
            .exposureDate(LocalDate.now())
            .build()

        restTemplate.postForEntity(
            "/api/v1/surveys",
            survey,
            HealthSurvey::class.java
        )

        // Verify Kafka message was sent
        verify(kafkaTemplate).send(
            eq("survey.submitted"),
            eq(anonymousId.toString()),
            any()
        )
    }

    @Test
    fun `multiple surveys from different users are saved separately`() {
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()

        val survey1 = HealthSurvey.builder()
            .anonymousId(user1)
            .hasFever(true)
            .hasCough(false)
            .build()

        val survey2 = HealthSurvey.builder()
            .anonymousId(user2)
            .hasFever(false)
            .hasCough(true)
            .build()

        val response1 = restTemplate.postForEntity("/api/v1/surveys", survey1, HealthSurvey::class.java)
        val response2 = restTemplate.postForEntity("/api/v1/surveys", survey2, HealthSurvey::class.java)

        assertEquals(HttpStatus.OK, response1.statusCode)
        assertEquals(HttpStatus.OK, response2.statusCode)

        assertNotEquals(response1.body?.id, response2.body?.id)
        assertEquals(user1, response1.body?.anonymousId)
        assertEquals(user2, response2.body?.anonymousId)

        val saved1 = surveyRepository.findById(response1.body?.id!!).orElse(null)
        val saved2 = surveyRepository.findById(response2.body?.id!!).orElse(null)

        assertNotNull(saved1)
        assertNotNull(saved2)
        assertEquals(true, saved1.hasFever)
        assertEquals(true, saved2.hasCough)
    }
}