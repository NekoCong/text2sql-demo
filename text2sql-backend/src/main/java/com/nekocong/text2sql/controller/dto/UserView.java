package com.nekocong.text2sql.controller.dto;

/**
 * The authenticated user, as returned to the client.
 *
 * @param id the user id
 * @param username the username
 * @param displayName the display name (may be {@code null})
 * @param role the role name ({@code admin} or {@code user})
 */
public record UserView(String id, String username, String displayName, String role) {}
