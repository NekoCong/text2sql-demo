package com.nekocong.text2sql.service;

import com.nekocong.text2sql.datasource.DataSourceRegistry;
import com.nekocong.text2sql.gbi.ChartInference;
import com.nekocong.text2sql.modelconfig.ModelResolver;
import com.nekocong.text2sql.nl2sql.ChartEvent;
import com.nekocong.text2sql.nl2sql.ExecutionListener;
import com.nekocong.text2sql.nl2sql.RowsEvent;
import com.nekocong.text2sql.nl2sql.SqlEvent;
import com.nekocong.text2sql.nl2sql.SqlGeneration;
import com.nekocong.text2sql.nl2sql.SqlGenerationService;
import com.nekocong.text2sql.nl2sql.StepEvent;
import com.nekocong.text2sql.nl2sql.Text2SqlEvent;
import com.nekocong.text2sql.nl2sql.Text2SqlResult;
import com.nekocong.text2sql.schema.SchemaProvider;
import com.nekocong.text2sql.security.DataScope;
import com.nekocong.text2sql.sql.DataScopeRewriter;
import com.nekocong.text2sql.sql.SqlExecutor;
import com.nekocong.text2sql.sql.SqlSafetyValidator;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * The deterministic NL2SQL pipeline: schema grounding → SQL generation → safety validation → data
 * permission rewrite → read-only execution → chart inference.
 *
 * <p>This is the product path — reliable and cheap (one model call, one query). The chat agent sits
 * <em>on top</em> of it. The model is resolved per caller, the schema is trimmed to the caller's
 * scope, and the generated SQL is rewritten for the caller before execution. Emits {@link
 * Text2SqlEvent}s so the web layer can stream structured progress.
 */
@Service
public class Text2SqlPipelineService {

    private final DataSourceRegistry registry;
    private final SchemaProvider schemaProvider;
    private final ModelResolver modelResolver;
    private final SqlGenerationService generationService;
    private final SqlSafetyValidator safetyValidator;
    private final DataScopeRewriter rewriter;
    private final SqlExecutor sqlExecutor;
    private final ChartInference chartInference;

    /**
     * Creates the pipeline.
     *
     * @param registry the datasource registry
     * @param schemaProvider the schema grounding provider
     * @param modelResolver the per-user model resolver
     * @param generationService the model-backed SQL generator
     * @param safetyValidator the read-only SQL validator
     * @param rewriter the data-permission rewriter
     * @param sqlExecutor the read-only executor
     * @param chartInference the Generative-BI chart inference
     */
    public Text2SqlPipelineService(
            DataSourceRegistry registry,
            SchemaProvider schemaProvider,
            ModelResolver modelResolver,
            SqlGenerationService generationService,
            SqlSafetyValidator safetyValidator,
            DataScopeRewriter rewriter,
            SqlExecutor sqlExecutor,
            ChartInference chartInference) {
        this.registry = registry;
        this.schemaProvider = schemaProvider;
        this.modelResolver = modelResolver;
        this.generationService = generationService;
        this.safetyValidator = safetyValidator;
        this.rewriter = rewriter;
        this.sqlExecutor = sqlExecutor;
        this.chartInference = chartInference;
    }

    /**
     * Runs the pipeline and returns the complete result.
     *
     * @param scope the caller's data scope
     * @param datasourceId the datasource id (or {@code null} for the default)
     * @param question the natural-language question
     * @return a mono emitting the result
     */
    public Mono<Text2SqlResult> run(DataScope scope, String datasourceId, String question) {
        return run(scope, datasourceId, question, ExecutionListener.noop());
    }

    /**
     * Runs the pipeline and returns the complete result, notifying an execution listener.
     *
     * @param scope the caller's data scope
     * @param datasourceId the datasource id (or {@code null} for the default)
     * @param question the natural-language question
     * @param listener notified with the enforced SQL / failure reason
     * @return a mono emitting the result
     */
    public Mono<Text2SqlResult> run(
            DataScope scope, String datasourceId, String question, ExecutionListener listener) {
        String id = resolveId(datasourceId);
        String dialect = dialectOf(id);
        return Mono.zip(schema(id, scope, question), modelResolver.resolve(scope.userId()))
                .flatMap(pair -> generationService.generate(pair.getT2(), dialect, pair.getT1(), question))
                .flatMap(generation -> execute(id, dialect, generation, scope, listener));
    }

    /**
     * Runs the pipeline and blocks for the result. Intended for non-web callers (agent tools,
     * tests) running on a bounded-elastic / tool-worker thread, never the event loop.
     *
     * @param scope the caller's data scope
     * @param datasourceId the datasource id (or {@code null} for the default)
     * @param question the natural-language question
     * @return the result
     */
    public Text2SqlResult runBlocking(DataScope scope, String datasourceId, String question) {
        return run(scope, datasourceId, question).block();
    }

    /**
     * Runs the pipeline and blocks, notifying an execution listener.
     *
     * @param scope the caller's data scope
     * @param datasourceId the datasource id (or {@code null} for the default)
     * @param question the natural-language question
     * @param listener notified with the enforced SQL / failure reason
     * @return the result
     */
    public Text2SqlResult runBlocking(
            DataScope scope, String datasourceId, String question, ExecutionListener listener) {
        return run(scope, datasourceId, question, listener).block();
    }

    /**
     * Runs the pipeline as a stream of progress + result events.
     *
     * @param scope the caller's data scope
     * @param datasourceId the datasource id (or {@code null} for the default)
     * @param question the natural-language question
     * @return a flux of pipeline events
     */
    public Flux<Text2SqlEvent> stream(DataScope scope, String datasourceId, String question) {
        return stream(scope, datasourceId, question, ExecutionListener.noop());
    }

    /**
     * Runs the pipeline as a stream, notifying an execution listener with the enforced SQL.
     *
     * @param scope the caller's data scope
     * @param datasourceId the datasource id (or {@code null} for the default)
     * @param question the natural-language question
     * @param listener notified with the enforced SQL / failure reason
     * @return a flux of pipeline events
     */
    public Flux<Text2SqlEvent> stream(
            DataScope scope, String datasourceId, String question, ExecutionListener listener) {
        String id = resolveId(datasourceId);
        String dialect = dialectOf(id);
        return Flux.concat(
                Flux.just(new StepEvent("introspect", "读取数据源表结构")),
                Mono.zip(schema(id, scope, question), modelResolver.resolve(scope.userId()))
                        .flatMapMany(
                                pair ->
                                        Flux.concat(
                                                Flux.just(new StepEvent("generate", "调用模型生成 SQL")),
                                                generationService
                                                        .generate(pair.getT2(), dialect, pair.getT1(), question)
                                                        .flatMapMany(gen -> toEvents(id, dialect, gen, scope, listener)))));
    }

    private Flux<Text2SqlEvent> toEvents(
            String id,
            String dialect,
            SqlGeneration generation,
            DataScope scope,
            ExecutionListener listener) {
        String safeSql = enforce(generation.sql(), scope);
        return Flux.concat(
                Flux.just(new SqlEvent(id, dialect, safeSql, generation.explanation())),
                Flux.just(new StepEvent("execute", "执行只读查询")),
                sqlExecutor
                        .execute(id, safeSql)
                        .doOnNext(rows -> listener.onSuccess(id, safeSql, rows.rowCount()))
                        .doOnError(error -> listener.onFailure(id, errorMessage(error)))
                        .flatMapMany(
                                rows ->
                                        Flux.just(
                                                new RowsEvent(rows), new ChartEvent(chartInference.infer(rows)))));
    }

    private Mono<Text2SqlResult> execute(
            String id,
            String dialect,
            SqlGeneration generation,
            DataScope scope,
            ExecutionListener listener) {
        String safeSql = enforce(generation.sql(), scope);
        return sqlExecutor
                .execute(id, safeSql)
                .doOnNext(rows -> listener.onSuccess(id, safeSql, rows.rowCount()))
                .doOnError(error -> listener.onFailure(id, errorMessage(error)))
                .map(
                        result ->
                                new Text2SqlResult(
                                        id, dialect, safeSql, generation.explanation(), result, chartInference.infer(result)));
    }

    private static String errorMessage(Throwable error) {
        String message = error.getMessage();
        return (message == null || message.isBlank()) ? error.getClass().getSimpleName() : message;
    }

    /**
     * Validates then rewrites: the statement that is shown to the user and executed is the enforced
     * one, never the raw model output.
     */
    private String enforce(String sql, DataScope scope) {
        String safeSql = safetyValidator.validateAndNormalize(sql);
        return rewriter.rewrite(safeSql, scope);
    }

    private Mono<String> schema(String id, DataScope scope, String question) {
        return Mono.fromCallable(() -> schemaProvider.renderForPrompt(id, scope, question))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String resolveId(String datasourceId) {
        String id = (datasourceId == null || datasourceId.isBlank()) ? registry.defaultId() : datasourceId;
        registry.definition(id); // fail fast on an unknown id
        return id;
    }

    private String dialectOf(String id) {
        return registry.definition(id).resolvedDialect().name();
    }
}
