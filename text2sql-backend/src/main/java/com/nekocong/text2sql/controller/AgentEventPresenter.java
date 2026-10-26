package com.nekocong.text2sql.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

/**
 * Maps AgentScope events onto the SSE event vocabulary the frontend already understands.
 *
 * <p>Tool steps become {@code step}, the pipeline's enforced SQL becomes {@code sql} (carrying the
 * datasource, row count and the "scope applied" flag), and failures become {@code error}. Token
 * deltas stay {@code delta}.
 */
@Component
public class AgentEventPresenter {

    private final ObjectMapper objectMapper;

    /**
     * Creates the presenter.
     *
     * @param objectMapper the shared Jackson mapper
     */
    public AgentEventPresenter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converts one agent event, when it is something the UI should show.
     *
     * @param event the AgentScope event
     * @return the SSE event to emit, or empty for events that carry no user-facing information
     */
    public Optional<ServerSentEvent<String>> present(AgentEvent event) {
        if (event instanceof TextBlockDeltaEvent delta) {
            return Optional.of(sse("delta", delta.getDelta()));
        }
        if (event instanceof ToolCallStartEvent start) {
            return Optional.of(
                    sse(
                            "step",
                            json(
                                    Map.of(
                                            "phase", "tool",
                                            "status", "start",
                                            "tool", nullSafe(start.getToolCallName())))));
        }
        if (event instanceof ToolResultEndEvent end) {
            return Optional.of(
                    sse(
                            "step",
                            json(
                                    Map.of(
                                            "phase", "tool",
                                            "status", "end",
                                            "state",
                                                    end.getState() == null
                                                            ? "unknown"
                                                            : end.getState().name().toLowerCase(Locale.ROOT),
                                            "tool", nullSafe(end.getToolCallName())))));
        }
        if (event instanceof CustomEvent custom) {
            if ("sql".equals(custom.getName())) {
                return Optional.of(sse("sql", json(custom.getValue())));
            }
            if ("error".equals(custom.getName())) {
                return Optional.of(sse("error", json(custom.getValue())));
            }
        }
        return Optional.empty();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder(data).event(event).build();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
