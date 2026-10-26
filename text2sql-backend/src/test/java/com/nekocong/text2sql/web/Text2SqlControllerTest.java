package com.nekocong.text2sql.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Web-layer tests for the text-to-SQL endpoints (authenticated).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Text2SqlControllerTest {

    @LocalServerPort
    private int port;

    private WebTestClient client;
    private String token;

    @Autowired
    private org.springframework.r2dbc.core.DatabaseClient db;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        token = TestAuth.registerAndLogin(client);
        seedScope(TestAuth.currentUserId(client, token));
    }

    private void seedScope(String userId) {
        insertScope(userId, "class_id", "1");
        insertScope(userId, "student_id", "1");
        insertScope(userId, "student_no", "2023001");
        db.sql(
                        "DELETE FROM datasource_access WHERE role = 'student' AND datasource_id = 'campus'")
                .then()
                .block();
        db.sql("INSERT INTO datasource_access (role, datasource_id) VALUES ('student', 'campus')")
                .then()
                .block();
    }

    private void insertScope(String userId, String type, String value) {
        db.sql(
                        "INSERT INTO user_scope (user_id, scope_type, scope_value) "
                                + "VALUES (:userId, :type, :value)")
                .bind("userId", userId)
                .bind("type", type)
                .bind("value", value)
                .then()
                .block();
    }

    @Test
    void healthEndpointIsPublic() {
        client.get()
                .uri("/api/text2sql/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .isEqualTo("OK");
    }

    @Test
    void listsConfiguredDatasources() {
        client.get()
                .uri("/api/text2sql/datasources")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].id")
                .isEqualTo("campus")
                .jsonPath("$[0].dialect")
                .isEqualTo("H2")
                .jsonPath("$[0].default")
                .isEqualTo(true);
    }

    @Test
    void queryIsForbiddenForAnAccountWithoutDataScope() {
        // Registration creates a student with no user_scope rows: the permission layer must reject
        // the request instead of running an unscoped query.
        String token = TestAuth.registerAndLogin(client);

        client.post()
                .uri("/api/text2sql/query")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":\"我的成绩\"}")
                .exchange()
                .expectStatus()
                .isForbidden();
    }
}
