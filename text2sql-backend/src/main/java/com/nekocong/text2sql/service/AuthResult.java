package com.nekocong.text2sql.service;

/**
 * Result of a successful authentication.
 *
 * @param token the JWT access token
 * @param expiresIn the token lifetime, in seconds
 * @param userId the user id
 * @param username the username
 * @param displayName the display name
 */
public record AuthResult(
        String token, long expiresIn, String userId, String username, String displayName) {}
