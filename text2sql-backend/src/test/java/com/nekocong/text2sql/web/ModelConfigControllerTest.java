package com.nekocong.text2sql.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Web-layer tests for per-user model configuration (CRUD; the API key is never returned).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ModelConfigControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    private WebTestClient client;
    private String token;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        token = TestAuth.registerAndLogin(client);
    }

    @Test
    void managesModelConfigs() {
        String body =
                client.post()
                        .uri("/api/model-configs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(
                                Map.of(
                                        "name", "My Qwen",
                                        "provider", "dashscope",
                                        "model", "qwen-plus",
                                        "apiKey", "sk-secret"))
                        .exchange()
                        .expectStatus()
                        .isCreated()
                        .expectBody(String.class)
                        .returnResult()
                        .getResponseBody();

        JsonNode created;
        String id;
        try {
            created = MAPPER.readTree(body);
            id = created.get("id").asText();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        // The API key must never be returned.
        assertThat(created.path("hasApiKey").asBoolean()).isTrue();
        assertThat(created.path("isDefault").asBoolean()).isTrue();
        assertThat(created.has("apiKey")).isFalse();

        client.get()
                .uri("/api/model-configs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].id")
                .isEqualTo(id);

        client.put()
                .uri("/api/model-configs/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Renamed", "provider", "dashscope", "model", "qwen-max"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("Renamed")
                .jsonPath("$.model")
                .isEqualTo("qwen-max")
                // Blank key on update keeps the stored one.
                .jsonPath("$.hasApiKey")
                .isEqualTo(true);

        client.delete()
                .uri("/api/model-configs/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    @Test
    void rejectsUnknownProvider() {
        client.post()
                .uri("/api/model-configs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("provider", "gemini", "model", "x"))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }
}
