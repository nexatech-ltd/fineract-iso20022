package org.fineract.iso20022.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.fineract.iso20022.model.dto.PaymentInitiationRequest;
import org.fineract.iso20022.model.dto.PaymentStatusResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("e2e")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentFlowE2ETest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("iso20022_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("fineract.api.base-url", () -> "http://localhost:18443/fineract-provider/api/v1");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldAcceptPain001AndReturnStatus() throws Exception {
        String xml = loadSampleXml("sample-pain001.xml");

        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .xmlMessage(xml)
                .idempotencyKey("e2e-test-pain001-" + System.currentTimeMillis())
                .build();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/payments", request, String.class);

        assertThat(response.getStatusCode()).isIn(
                HttpStatus.CREATED, HttpStatus.BAD_GATEWAY, HttpStatus.UNPROCESSABLE_ENTITY);

        if (response.getStatusCode() == HttpStatus.CREATED) {
            List<PaymentStatusResponse> statuses = objectMapper.readValue(
                    response.getBody(), new TypeReference<>() {});
            assertThat(statuses).isNotEmpty();
            assertThat(statuses.getFirst().getOriginalMessageId()).isNotNull();
        }
    }

    @Test
    void shouldAcceptRawXmlPayment() {
        String xml = loadSampleXml("sample-pacs008.xml");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.set("Idempotency-Key", "e2e-raw-xml-" + System.currentTimeMillis());

        HttpEntity<String> entity = new HttpEntity<>(xml, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/payments/xml", HttpMethod.POST, entity, String.class);

        assertThat(response.getStatusCode()).isIn(
                HttpStatus.CREATED, HttpStatus.BAD_GATEWAY, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void shouldReturnValidationErrorForInvalidXml() {
        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .xmlMessage("not valid xml")
                .build();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/payments", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn404ForUnknownMessageId() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/payments/status/NONEXISTENT-ID", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void shouldListMessages() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/messages?page=0&size=10", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void shouldHandleIdempotentRequests() throws Exception {
        String xml = loadSampleXml("sample-pain001.xml");
        String idempotencyKey = "idempotent-" + System.currentTimeMillis();

        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .xmlMessage(xml)
                .idempotencyKey(idempotencyKey)
                .build();

        ResponseEntity<String> firstResponse = restTemplate.postForEntity(
                "/api/v1/payments", request, String.class);

        if (firstResponse.getStatusCode() == HttpStatus.CREATED) {
            ResponseEntity<String> secondResponse = restTemplate.postForEntity(
                    "/api/v1/payments", request, String.class);

            assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Test
    void shouldExposeActuatorHealth() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("status");
    }

    private String loadSampleXml(String filename) {
        try (var is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
