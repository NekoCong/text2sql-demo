package com.nekocong.text2sql.user;

import com.nekocong.text2sql.security.Roles;

/**
 * An application user.
 *
 * @param id the user id (UUID)
 * @param username the unique username
 * @param passwordHash the BCrypt password hash
 * @param displayName the display name
 * @param role the role name ({@code admin} or {@code user})
 */
public record AppUser(
        String id, String username, String passwordHash, String displayName, String role) {

    /**
     * Reports whether this user has the admin role.
     *
     * @return {@code true} for admins
     */
    public boolean isAdmin() {
        return Roles.ADMIN.equalsIgnoreCase(role);
    }
}
