package com.nekocong.text2sql.user;

/**
 * An application user.
 *
 * @param id the user id (UUID)
 * @param username the unique username
 * @param passwordHash the BCrypt password hash
 * @param displayName the display name
 */
public record AppUser(String id, String username, String passwordHash, String displayName) {}
