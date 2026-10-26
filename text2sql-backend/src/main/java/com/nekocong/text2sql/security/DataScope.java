package com.nekocong.text2sql.security;

import java.util.Set;

/**
 * The resolved data scope for one request: who the caller is, which role they carry, and the
 * concrete values the permission layer needs to filter by.
 *
 * <p>Immutable and self-contained: it is passed from the web layer into the pipeline, and (for the
 * agent path) placed into the AgentScope runtime context, so no layer has to re-read the identity.
 *
 * @param userId the authenticated user id
 * @param role the canonical role ({@code admin}, {@code student} or {@code counselor})
 * @param classIds the classes in scope: the classes a counselor manages, or the student's own class
 * @param studentId the caller's own business-database student id (students only)
 * @param studentNo the caller's own student number (students only)
 * @param allowedDatasources datasource ids the role may query; ignored for admins, who see all
 */
public record DataScope(
        String userId,
        String role,
        Set<Long> classIds,
        Long studentId,
        String studentNo,
        Set<String> allowedDatasources) {

    /** Normalizes the collections so the scope is structurally immutable. */
    public DataScope {
        classIds = classIds == null ? Set.of() : Set.copyOf(classIds);
        allowedDatasources = allowedDatasources == null ? Set.of() : Set.copyOf(allowedDatasources);
    }

    /**
     * Creates an unrestricted scope for an administrator.
     *
     * @param userId the admin user id
     * @return the admin scope
     */
    public static DataScope admin(String userId) {
        return new DataScope(userId, Roles.ADMIN, Set.of(), null, null, Set.of());
    }

    /** @return {@code true} when this scope bypasses table, row and datasource restrictions */
    public boolean isAdmin() {
        return Roles.ADMIN.equals(role);
    }

    /** @return {@code true} when the caller is a student */
    public boolean isStudent() {
        return Roles.STUDENT.equals(role);
    }

    /** @return {@code true} when the caller is a counselor */
    public boolean isCounselor() {
        return Roles.COUNSELOR.equals(role);
    }
}
