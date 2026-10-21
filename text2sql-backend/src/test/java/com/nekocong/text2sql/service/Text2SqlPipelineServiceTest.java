package com.nekocong.text2sql.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nekocong.text2sql.datasource.DataSourceRegistry;
import com.nekocong.text2sql.gbi.ChartInference;
import com.nekocong.text2sql.modelconfig.ModelResolver;
import com.nekocong.text2sql.nl2sql.ChartEvent;
import com.nekocong.text2sql.nl2sql.PromptAssembler;
import com.nekocong.text2sql.nl2sql.RowsEvent;
import com.nekocong.text2sql.nl2sql.SqlEvent;
import com.nekocong.text2sql.nl2sql.SqlGeneration;
import com.nekocong.text2sql.nl2sql.SqlGenerationService;
import com.nekocong.text2sql.nl2sql.SqlResultParser;
import com.nekocong.text2sql.nl2sql.StepEvent;
import com.nekocong.text2sql.nl2sql.Text2SqlEvent;
import com.nekocong.text2sql.nl2sql.Text2SqlResult;
import com.nekocong.text2sql.prompt.PromptCatalog;
import com.nekocong.text2sql.schema.FullSchemaProvider;
import com.nekocong.text2sql.sql.SqlExecutionException;
import com.nekocong.text2sql.sql.SqlExecutor;
import com.nekocong.text2sql.sql.SqlSafetyException;
import com.nekocong.text2sql.sql.SqlSafetyValidator;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Exercises the full deterministic pipeline (schema → generate → validate → execute → chart) with a
 * stubbed model + resolver, so orchestration is verified without an LLM.
 */
@SpringBootTest
class Text2SqlPipelineServiceTest {

    /** A no-op model; the stubbed generation service ignores it. */
    private static final Model DUMMY_MODEL =
            new Model() {
                @Override
                public Flux<ChatResponse> stream(
                        List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                    return Flux.empty();
                }

                @Override
                public String getModelName() {
                    return "test-dummy";
                }
            };

    @Autowired
    private DataSourceRegistry registry;

    @Autowired
    private FullSchemaProvider schemaProvider;

    @Autowired
    private SqlSafetyValidator safetyValidator;

    @Autowired
    private SqlExecutor sqlExecutor;

    private Text2SqlPipelineService pipelineReturning(String sql, String explanation) {
        SqlGenerationService stub =
                new SqlGenerationService(new PromptAssembler(new PromptCatalog()), new SqlResultParser(new ObjectMapper())) {
                    @Override
                    public Mono<SqlGeneration> generate(
                            Model model, String dialect, String schema, String question) {
                        return Mono.just(new SqlGeneration(sql, explanation));
                    }
                };
        ModelResolver resolver =
                new ModelResolver(null, null, null) {
                    @Override
                    public Mono<Model> resolve(String userId) {
                        return Mono.just(DUMMY_MODEL);
                    }
                };
        return new Text2SqlPipelineService(
                registry, schemaProvider, resolver, stub, safetyValidator, sqlExecutor, new ChartInference());
    }

    @Test
    void runsEndToEndWithStubbedModel() {
        Text2SqlPipelineService pipeline =
                pipelineReturning("SELECT COUNT(*) AS order_count FROM orders", "counts all orders");

        Text2SqlResult result = pipeline.run("1", null, "how many orders are there?").block();

        assertThat(result.datasourceId()).isEqualTo("demo");
        assertThat(result.sql()).isEqualTo("SELECT COUNT(*) AS order_count FROM orders");
        assertThat(result.result().rowCount()).isEqualTo(1);
        assertThat(result.result().columns()).anyMatch(c -> c.name().equalsIgnoreCase("order_count"));
    }

    @Test
    void rejectsUnsafeGeneratedSqlBeforeExecution() {
        Text2SqlPipelineService pipeline = pipelineReturning("DROP TABLE orders", null);

        assertThatThrownBy(() -> pipeline.run("1", null, "drop orders").block())
                .isInstanceOf(SqlSafetyException.class);
    }

    @Test
    void surfacesExecutionErrors() {
        Text2SqlPipelineService pipeline = pipelineReturning("SELECT * FROM does_not_exist", null);

        assertThatThrownBy(() -> pipeline.run("1", null, "bad query").block())
                .isInstanceOf(SqlExecutionException.class);
    }

    @Test
    void streamEmitsStepsSqlRowsAndChartInOrder() {
        Text2SqlPipelineService pipeline =
                pipelineReturning("SELECT id FROM orders ORDER BY id", "lists order ids");

        List<Text2SqlEvent> events = pipeline.stream("1", null, "list order ids").collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.get(0)).isInstanceOf(StepEvent.class);
        assertThat(events).anySatisfy(e -> assertThat(e).isInstanceOf(SqlEvent.class));

        int rowsIndex = indexOf(events, RowsEvent.class);
        int chartIndex = indexOf(events, ChartEvent.class);
        assertThat(rowsIndex).isGreaterThanOrEqualTo(0);
        assertThat(chartIndex).isGreaterThan(rowsIndex);

        RowsEvent rows = (RowsEvent) events.get(rowsIndex);
        assertThat(rows.result().rowCount()).isPositive();
    }

    private static int indexOf(List<Text2SqlEvent> events, Class<? extends Text2SqlEvent> type) {
        for (int i = 0; i < events.size(); i++) {
            if (type.isInstance(events.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
