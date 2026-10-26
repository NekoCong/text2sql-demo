package com.nekocong.text2sql.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verifies scope resolution against the real application schema: students resolve to themselves,
 * counselors to their class set, admins to an unrestricted scope, and everything unconfigured
 * fails closed with 403.
 */
@SpringBootTest
class DataScopeResolverTest {

    @Autowired
    private DataScopeResolver resolver;

    @Autowired
    private DatabaseClient client;

    @Test
    void studentResolvesToItsOwnRowAndClass() {
        String userId = "scope-test-student";
        scope(userId, "class_id", "7");
        scope(userId, "student_id", "70");
        scope(userId, "student_no", "2023070");
        grant("student", "campus");

        DataScope scope = resolver.resolve(userId, "student").block();

        assertThat(scope).isNotNull();
        assertThat(scope.isStudent()).isTrue();
        assertThat(scope.classIds()).containsExactly(7L);
        assertThat(scope.studentId()).isEqualTo(70L);
        assertThat(scope.studentNo()).isEqualTo("2023070");
        assertThat(scope.allowedDatasources()).contains("campus");
    }

    @Test
    void counselorResolvesToEveryManagedClass() {
        String userId = "scope-test-counselor";
        scope(userId, "class_id", "8");
        scope(userId, "class_id", "9");
        grant("counselor", "campus");

        DataScope scope = resolver.resolve(userId, "counselor").block();

        assertThat(scope).isNotNull();
        assertThat(scope.isCounselor()).isTrue();
        assertThat(scope.classIds()).containsExactlyInAnyOrder(8L, 9L);
        assertThat(scope.studentId()).isNull();
        assertThat(scope.studentNo()).isNull();
    }

    @Test
    void adminResolvesWithoutScopeRows() {
        DataScope scope = resolver.resolve("scope-test-admin", "admin").block();

        assertThat(scope).isNotNull();
        assertThat(scope.isAdmin()).isTrue();
        assertThat(scope.classIds()).isEmpty();
        assertThat(scope.studentId()).isNull();
    }

    @Test
    void unknownRoleIsRejected() {
        assertDenied(resolver.resolve("scope-test-legacy", "user"), "角色");
        assertDenied(resolver.resolve("scope-test-none", null), "角色");
    }

    @Test
    void studentWithoutScopeIsRejected() {
        assertDenied(resolver.resolve("scope-test-unconfigured", "student"), "未配置数据范围");
    }

    @Test
    void invalidScopeValueIsRejected() {
        String userId = "scope-test-invalid";
        scope(userId, "class_id", "not-a-number");

        assertDenied(resolver.resolve(userId, "student"), "配置无效");
    }

    private void assertDenied(reactor.core.publisher.Mono<DataScope> result, String messagePart) {
        assertThatThrownBy(result::block)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining(messagePart);
    }

    private void scope(String userId, String type, String value) {
        client.sql(
                        "DELETE FROM user_scope WHERE user_id = :userId AND scope_type = :type "
                                + "AND scope_value = :value")
                .bind("userId", userId)
                .bind("type", type)
                .bind("value", value)
                .then()
                .block();
        client.sql(
                        "INSERT INTO user_scope (user_id, scope_type, scope_value) "
                                + "VALUES (:userId, :type, :value)")
                .bind("userId", userId)
                .bind("type", type)
                .bind("value", value)
                .then()
                .block();
    }

    private void grant(String role, String datasourceId) {
        client.sql("DELETE FROM datasource_access WHERE role = :role AND datasource_id = :datasourceId")
                .bind("role", role)
                .bind("datasourceId", datasourceId)
                .then()
                .block();
        client.sql(
                        "INSERT INTO datasource_access (role, datasource_id) VALUES (:role, :datasourceId)")
                .bind("role", role)
                .bind("datasourceId", datasourceId)
                .then()
                .block();
    }
}
