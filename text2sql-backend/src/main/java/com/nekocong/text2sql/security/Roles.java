package com.nekocong.text2sql.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Role names and helpers shared by token issuance and authorization checks.
 *
 * <p>Two roles are supported: {@link #ADMIN} may read and modify every conversation, while
 * {@link #USER} is restricted to the rows it owns.
 */
public final class Roles {

    /** JWT claim carrying the role. */
    public static final String CLAIM = "role";

    /** Full-access role. */
    public static final String ADMIN = "admin";

    /** Default role: only the caller's own rows are visible. */
    public static final String USER = "user";

    private Roles() {}

    /**
     * Canonicalizes a role name, falling back to {@link #USER} for anything unrecognized.
     *
     * @param role the raw role value (may be {@code null})
     * @return {@link #ADMIN} or {@link #USER}
     */
    public static String normalize(String role) {
        return ADMIN.equalsIgnoreCase(role) ? ADMIN : USER;
    }

    /**
     * Reports whether a bearer token carries the admin role.
     *
     * @param jwt the bearer token (may be {@code null})
     * @return {@code true} when the token's role is {@link #ADMIN}
     */
    public static boolean isAdmin(Jwt jwt) {
        return jwt != null && ADMIN.equalsIgnoreCase(jwt.getClaimAsString(CLAIM));
    }
}
