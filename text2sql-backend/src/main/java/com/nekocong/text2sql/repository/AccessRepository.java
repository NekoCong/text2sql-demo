package com.nekocong.text2sql.repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Data-access layer for the two access-control tables: {@code user_scope} (principal attributes)
 * and {@code datasource_access} (role -> datasource whitelist).
 *
 * <p>These live in the application database on purpose: resolving who the caller is must never
 * require reading the protected business database.
 */
@Repository
public class AccessRepository {

    private final DatabaseClient client;

    /**
     * Creates the repository.
     *
     * @param client the R2DBC database client
     */
    public AccessRepository(DatabaseClient client) {
        this.client = client;
    }

    /** One {@code user_scope} row. */
    public record ScopeRow(String scopeType, String scopeValue) {}

    /**
     * Reads every scope attribute of a user.
     *
     * @param userId the user id
     * @return the scope rows, in insertion order
     */
    public Mono<List<ScopeRow>> scopes(String userId) {
        return client.sql("SELECT scope_type, scope_value FROM user_scope WHERE user_id = :userId")
                .bind("userId", userId)
                .map(
                        (row, meta) ->
                                new ScopeRow(
                                        row.get("scope_type", String.class),
                                        row.get("scope_value", String.class)))
                .all()
                .collectList();
    }

    /**
     * Reads the datasources a role is allowed to query.
     *
     * @param role the canonical role
     * @return the allowed datasource ids
     */
    public Mono<Set<String>> datasourcesForRole(String role) {
        return client.sql("SELECT datasource_id FROM datasource_access WHERE role = :role")
                .bind("role", role)
                .map((row, meta) -> row.get("datasource_id", String.class))
                .all()
                .collectList()
                .map(LinkedHashSet::new);
    }
}
