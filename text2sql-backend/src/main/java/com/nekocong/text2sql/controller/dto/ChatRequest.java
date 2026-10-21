package com.nekocong.text2sql.controller.dto;

/**
 * Request body for the synchronous text-to-SQL endpoint.
 *
 * @param message the natural-language question
 * @param sessionId optional session identifier (defaults to {@code "default"})
 * @param userId optional user identifier (defaults to {@code "anonymous"})
 * @param datasourceId optional datasource id (defaults to the configured default datasource)
 */
public record ChatRequest(String message, String sessionId, String userId, String datasourceId) {

    /**
     * Returns a non-blank session id.
     *
     * @return the session id or {@code "default"}
     */
    public String sessionIdOrDefault() {
        return (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
    }

    /**
     * Returns a non-blank user id.
     *
     * @return the user id or {@code "anonymous"}
     */
    public String userIdOrDefault() {
        return (userId == null || userId.isBlank()) ? "anonymous" : userId;
    }
}
