package com.nekocong.text2sql.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nekocong.text2sql.conversation.Conversation;
import com.nekocong.text2sql.controller.dto.ChatRequest;
import com.nekocong.text2sql.nl2sql.ChartEvent;
import com.nekocong.text2sql.nl2sql.RowsEvent;
import com.nekocong.text2sql.nl2sql.SqlEvent;
import com.nekocong.text2sql.nl2sql.StepEvent;
import com.nekocong.text2sql.nl2sql.Text2SqlEvent;
import com.nekocong.text2sql.nl2sql.Text2SqlResult;
import com.nekocong.text2sql.service.ConversationService;
import com.nekocong.text2sql.service.Text2SqlAgentService;
import com.nekocong.text2sql.service.Text2SqlPipelineService;
import io.agentscope.core.event.TextBlockDeltaEvent;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controller layer for text-to-SQL (authenticated). Delegates to the pipeline / agent / conversation
 * services.
 *
 * <p>The pipeline path streams {@code conversation} / {@code step} / {@code sql} / {@code rows} /
 * {@code chart} events and persists the exchange to the conversation.
 */
@RestController
@RequestMapping("/api/text2sql")
public class Text2SqlController {

    private final Text2SqlPipelineService pipeline;
    private final Text2SqlAgentService agentService;
    private final ConversationService conversations;
    private final ObjectMapper objectMapper;

    /**
     * Creates the controller.
     *
     * @param pipeline the deterministic NL2SQL pipeline
     * @param agentService the AgentScope chat agent service
     * @param conversations the conversation service (persistence)
     * @param objectMapper the shared Jackson mapper
     */
    public Text2SqlController(
            Text2SqlPipelineService pipeline,
            Text2SqlAgentService agentService,
            ConversationService conversations,
            ObjectMapper objectMapper) {
        this.pipeline = pipeline;
        this.agentService = agentService;
        this.conversations = conversations;
        this.objectMapper = objectMapper;
    }

    /**
     * Streams the NL2SQL pipeline as Server-Sent Events, persisting the exchange.
     *
     * @param jwt the bearer token
     * @param message the natural-language question
     * @param datasourceId optional datasource id
     * @param conversationId optional conversation id (a new one is created when omitted)
     * @return a flux of SSE events
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String message,
            @RequestParam(required = false) String datasourceId,
            @RequestParam(required = false) String conversationId) {

        String userId = jwt.getSubject();
        AtomicReference<SqlEvent> sqlRef = new AtomicReference<>();
        AtomicReference<RowsEvent> rowsRef = new AtomicReference<>();
        AtomicReference<ChartEvent> chartRef = new AtomicReference<>();

        return resolveConversation(userId, conversationId, datasourceId, message)
                .flatMapMany(
                        convo -> {
                            Flux<ServerSentEvent<String>> head =
                                    Flux.just(
                                            sse(
                                                    "conversation",
                                                    json(Map.of("id", convo.id(), "title", convo.title()))));
                            Flux<ServerSentEvent<String>> body =
                                    pipeline.stream(userId, datasourceId, message)
                                            .doOnNext(
                                                    event -> {
                                                        if (event instanceof SqlEvent sql) {
                                                            sqlRef.set(sql);
                                                        } else if (event instanceof RowsEvent rows) {
                                                            rowsRef.set(rows);
                                                        } else if (event instanceof ChartEvent chart) {
                                                            chartRef.set(chart);
                                                        }
                                                    })
                                            .map(this::toSse);
                            Mono<ServerSentEvent<String>> tail =
                                    // Deferred so the captured sql/rows/chart are read *after* the
                                    // stream has emitted them (otherwise the assistant row is saved
                                    // empty, and reloading the conversation shows blank answers).
                                    Mono.defer(() -> persistAssistant(convo.id(), sqlRef, rowsRef, chartRef))
                                            .thenReturn(sse("done", "[DONE]"));
                            return Flux.concat(head, body, tail);
                        })
                .onErrorResume(ex -> Flux.just(sse("error", errorMessage(ex))));
    }

    /**
     * Runs the pipeline and returns the complete result (no persistence).
     *
     * @param jwt the bearer token
     * @param request the chat request
     * @return the structured result
     */
    @PostMapping("/query")
    public Mono<Text2SqlResult> query(@AuthenticationPrincipal Jwt jwt, @RequestBody ChatRequest request) {
        return pipeline.run(jwt.getSubject(), request.datasourceId(), request.message());
    }

    /**
     * Streams the AgentScope chat agent (which may call the SQL tools).
     *
     * @param jwt the bearer token
     * @param message the natural-language question
     * @param sessionId optional session identifier
     * @return a flux of SSE token events
     */
    @GetMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> agentStream(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {
        String userId = jwt.getSubject();
        return agentService
                .stream(message, sessionId, userId)
                .filter(event -> event instanceof TextBlockDeltaEvent)
                .map(event -> sse("delta", ((TextBlockDeltaEvent) event).getDelta()))
                .concatWith(Mono.just(sse("done", "[DONE]")))
                .onErrorResume(ex -> Flux.just(sse("error", errorMessage(ex))));
    }

    /**
     * Liveness probe.
     *
     * @return {@code "OK"}
     */
    @GetMapping("/health")
    public Mono<String> health() {
        return Mono.just("OK");
    }

    private Mono<Conversation> resolveConversation(
            String userId, String conversationId, String datasourceId, String message) {
        if (conversationId != null && !conversationId.isBlank()) {
            return conversations
                    .requireOwned(conversationId, userId)
                    .flatMap(
                            convo -> conversations.appendUserMessage(convo.id(), message).thenReturn(convo));
        }
        return conversations
                .create(userId, titleFrom(message), datasourceId, null)
                .flatMap(convo -> conversations.appendUserMessage(convo.id(), message).thenReturn(convo));
    }

    private Mono<Void> persistAssistant(
            String conversationId,
            AtomicReference<SqlEvent> sqlRef,
            AtomicReference<RowsEvent> rowsRef,
            AtomicReference<ChartEvent> chartRef) {
        SqlEvent sql = sqlRef.get();
        String content = (sql != null && sql.explanation() != null) ? sql.explanation() : "";
        String sqlText = sql == null ? null : sql.sql();
        String chartJson = chartRef.get() == null ? null : json(chartRef.get().chart());
        String rowsJson = rowsRef.get() == null ? null : json(rowsRef.get().result());
        return conversations
                .appendAssistantMessage(conversationId, content, sqlText, chartJson, rowsJson)
                .then(conversations.touchConversation(conversationId));
    }

    private ServerSentEvent<String> toSse(Text2SqlEvent event) {
        if (event instanceof StepEvent step) {
            return sse("step", json(Map.of("phase", step.phase(), "detail", step.detail())));
        }
        if (event instanceof SqlEvent sql) {
            return sse("sql", json(sql));
        }
        if (event instanceof RowsEvent rows) {
            return sse("rows", json(rows.result()));
        }
        if (event instanceof ChartEvent chart) {
            return sse("chart", json(chart.chart()));
        }
        return sse("message", "");
    }

    private static String titleFrom(String message) {
        String trimmed = message == null ? "" : message.strip();
        return trimmed.length() > 20 ? trimmed.substring(0, 20) : trimmed;
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

    private static String errorMessage(Throwable ex) {
        String message = ex.getMessage();
        return (message == null || message.isBlank()) ? ex.getClass().getSimpleName() : message;
    }
}
