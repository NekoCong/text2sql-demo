package com.nekocong.text2sql.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.ToolResultState;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

/**
 * The agent stream must surface steps, the enforced SQL and failures; everything else stays out of
 * the SSE stream.
 */
class AgentEventPresenterTest {

    private final AgentEventPresenter presenter =
            new AgentEventPresenter(new ObjectMapper());

    @Test
    void tokenDeltasStayDeltas() {
        ServerSentEvent<String> event =
                present(new TextBlockDeltaEvent("r1", "b1", "你好"));

        assertThat(event.event()).isEqualTo("delta");
        assertThat(event.data()).isEqualTo("你好");
    }

    @Test
    void toolCallsBecomeSteps() {
        ServerSentEvent<String> start =
                present(new ToolCallStartEvent("r1", "c1", "query_database"));
        assertThat(start.event()).isEqualTo("step");
        assertThat(start.data()).contains("query_database").contains("\"status\":\"start\"");

        ServerSentEvent<String> end =
                present(new ToolResultEndEvent("r1", "c1", "query_database", ToolResultState.ERROR));
        assertThat(end.event()).isEqualTo("step");
        assertThat(end.data()).contains("query_database").contains("\"state\":\"error\"");
    }

    @Test
    void sqlEventsCarryTheEnforcedStatement() {
        ServerSentEvent<String> event =
                present(
                        new CustomEvent(
                                "sql",
                                Map.of(
                                        "datasourceId", "campus",
                                        "sql", "SELECT 1",
                                        "rowCount", 1,
                                        "scopeApplied", true)));

        assertThat(event.event()).isEqualTo("sql");
        assertThat(event.data()).contains("SELECT 1").contains("\"scopeApplied\":true");
    }

    @Test
    void failureEventsBecomeErrors() {
        ServerSentEvent<String> event =
                present(new CustomEvent("error", Map.of("message", "表 salary 不在当前数据范围内")));

        assertThat(event.event()).isEqualTo("error");
        assertThat(event.data()).contains("salary");
    }

    @Test
    void unrelatedEventsAreDropped() {
        assertThat(presenter.present(new CustomEvent("noop", Map.of("k", "v")))).isEmpty();
    }

    private ServerSentEvent<String> present(io.agentscope.core.event.AgentEvent event) {
        Optional<ServerSentEvent<String>> result = presenter.present(event);
        assertThat(result).isPresent();
        return result.orElseThrow();
    }
}
