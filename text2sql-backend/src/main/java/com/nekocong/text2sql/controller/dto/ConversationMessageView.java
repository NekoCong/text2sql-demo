package com.nekocong.text2sql.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A persisted message as returned to clients. Chart and rows are embedded as JSON objects.
 *
 * @param id the message id
 * @param role {@code user} or {@code assistant}
 * @param content the text content
 * @param sql the generated SQL
 * @param chart the chart spec, or {@code null}
 * @param rows the result rows, or {@code null}
 * @param createdAt epoch millis
 */
public record ConversationMessageView(
        String id,
        String role,
        String content,
        String sql,
        JsonNode chart,
        JsonNode rows,
        long createdAt) {}
