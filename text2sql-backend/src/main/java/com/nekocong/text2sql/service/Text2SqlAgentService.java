package com.nekocong.text2sql.service;

import com.nekocong.text2sql.config.HarnessAgentFactory;
import com.nekocong.text2sql.modelconfig.ModelResolver;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Bridges the web layer to the AgentScope agent runtime.
 *
 * <p>Both methods stream the raw {@link AgentEvent} flux; token level aggregation lives in the
 * controller / frontend. The agent is built with the requesting user's configured model. Agent
 * execution is offloaded to a bounded-elastic scheduler so the Netty event loop is never blocked.
 */
@Service
public class Text2SqlAgentService {

    private final HarnessAgentFactory agentFactory;
    private final ModelResolver modelResolver;

    /**
     * Creates the service.
     *
     * @param agentFactory factory building a fresh, single-use agent per request
     * @param modelResolver resolves the user's model
     */
    public Text2SqlAgentService(HarnessAgentFactory agentFactory, ModelResolver modelResolver) {
        this.agentFactory = agentFactory;
        this.modelResolver = modelResolver;
    }

    /**
     * Streams the agent's events for a single user question.
     *
     * @param message the natural-language question
     * @param sessionId session identifier used for conversation state
     * @param userId user id (the JWT subject); selects that user's configured model
     * @return a cold flux of agent events
     */
    public Flux<AgentEvent> stream(String message, String sessionId, String userId) {
        return modelResolver
                .resolve(normalizeUserId(userId))
                .flatMapMany(
                        model ->
                                Flux.defer(
                                        () -> {
                                            RuntimeContext ctx =
                                                    RuntimeContext.builder()
                                                            .sessionId(sessionId)
                                                            .userId(userId)
                                                            .build();
                                            return agentFactory.create(model).streamEvents(new UserMessage(message), ctx);
                                        }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Collects the full textual answer for a single user question.
     *
     * @param message the natural-language question
     * @param sessionId session identifier used for conversation state
     * @param userId user id (the JWT subject); selects that user's configured model
     * @return the concatenated assistant text
     */
    public Mono<String> query(String message, String sessionId, String userId) {
        return stream(message, sessionId, userId)
                .filter(event -> event instanceof TextBlockDeltaEvent)
                .map(event -> ((TextBlockDeltaEvent) event).getDelta())
                .collect(StringBuilder::new, StringBuilder::append)
                .map(StringBuilder::toString);
    }

    private static String normalizeUserId(String userId) {
        if (userId == null || userId.isBlank() || "anonymous".equals(userId) || "default".equals(userId)) {
            return null;
        }
        return userId;
    }
}
