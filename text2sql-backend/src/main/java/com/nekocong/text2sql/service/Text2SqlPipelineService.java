package com.nekocong.text2sql.service;

import com.nekocong.text2sql.datasource.DataSourceRegistry;
import com.nekocong.text2sql.gbi.ChartInference;
import com.nekocong.text2sql.modelconfig.ModelResolver;
import com.nekocong.text2sql.nl2sql.ChartEvent;
import com.nekocong.text2sql.nl2sql.RowsEvent;
import com.nekocong.text2sql.nl2sql.SqlEvent;
import com.nekocong.text2sql.nl2sql.SqlGeneration;
import com.nekocong.text2sql.nl2sql.SqlGenerationService;
import com.nekocong.text2sql.nl2sql.StepEvent;
import com.nekocong.text2sql.nl2sql.Text2SqlEvent;
import com.nekocong.text2sql.nl2sql.Text2SqlResult;
import com.nekocong.text2sql.schema.SchemaProvider;
import com.nekocong.text2sql.sql.SqlExecutor;
import com.nekocong.text2sql.sql.SqlSafetyValidator;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * The deterministic NL2SQL pipeline: schema grounding → SQL generation → safety validation →
 * read-only execution → chart inference.
 *
 * <p>This is the product path — reliable and cheap (one model call, one query). The chat agent sits
 * <em>on top</em> of it. The model is resolved per request (the user's configured model, else the
 * global default). Emits {@link Text2SqlEvent}s so the web layer can stream structured progress.
 */
@Service
public class Text2SqlPipelineService {

    private final DataSourceRegistry registry;
    private final SchemaProvider schemaProvider;
    private final ModelResolver modelResolver;
    private final SqlGenerationService generationService;
    private final SqlSafetyValidator safetyValidator;
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
     * @param sqlExecutor the read-only executor
     * @param chartInference the Generative-BI chart inference
     */
    public Text2SqlPipelineService(
            DataSourceRegistry registry,
            SchemaProvider schemaProvider,
            ModelResolver modelResolver,
            SqlGenerationService generationService,
            SqlSafetyValidator safetyValidator,
            SqlExecutor sqlExecutor,
            ChartInference chartInference) {
        this.registry = registry;
        this.schemaProvider = schemaProvider;
        this.modelResolver = modelResolver;
        this.generationService = generationService;
        this.safetyValidator = safetyValidator;
        this.sqlExecutor = sqlExecutor;
        this.chartInference = chartInference;
    }

    /**
     * Runs the pipeline and returns the complete result.
     *
     * @param userId the requesting user (may be {@code null} → global model)
     * @param datasourceId the datasource id (or {@code null} for the default)
     * @param question the natural-language question
     * @return a mono emitting the result
     */
    public Mono<Text2SqlResult> run(String userId, String datasourceId, String question) {
        String id = resolveId(datasourceId);
        String dialect = dialectOf(id);
        return Mono.zip(schema(id, question), modelResolver.resolve(userId))
                .flatMap(pair -> generationService.generate(pair.getT2(), dialect, pair.getT1(), question))
                .flatMap(generation -> execute(id, dialect, generation));
    }

    /**
     * Runs the pipeline and blocks for the result. Intended for non-web callers (agent tools,
     * tests) running on a bounded-elastic / tool-worker thread, never the event loop.
     *
     * @param userId the requesting user (may be {@code null} → global model)
     * @param datasourceId the datasource id (or {@code null} for the default)
     * @param question the natural-language question
     * @return the result
     */
    public Text2SqlResult runBlocking(String userId, String datasourceId, String question) {
        return run(userId, datasourceId, question).block();
    }

    /**
     * Runs the pipeline as a stream of progress + result events.
     *
     * @param userId the requesting user (may be {@code null} → global model)
     * @param datasourceId the datasource id (or {@code null} for the default)
     * @param question the natural-language question
     * @return a flux of pipeline events
     */
    public Flux<Text2SqlEvent> stream(String userId, String datasourceId, String question) {
        String id = resolveId(datasourceId);
        String dialect = dialectOf(id);
        return Flux.concat(
                Flux.just(new StepEvent("introspect", "读取数据源表结构")),
                Mono.zip(schema(id, question), modelResolver.resolve(userId))
                        .flatMapMany(
                                pair ->
                                        Flux.concat(
                                                Flux.just(new StepEvent("generate", "调用模型生成 SQL")),
                                                generationService
                                                        .generate(pair.getT2(), dialect, pair.getT1(), question)
                                                        .flatMapMany(gen -> toEvents(id, dialect, gen)))));
    }

    private Flux<Text2SqlEvent> toEvents(String id, String dialect, SqlGeneration generation) {
        String safeSql = safetyValidator.validateAndNormalize(generation.sql());
        return Flux.concat(
                Flux.just(new SqlEvent(id, dialect, safeSql, generation.explanation())),
                Flux.just(new StepEvent("execute", "执行只读查询")),
                sqlExecutor
                        .execute(id, safeSql)
                        .flatMapMany(
                                rows ->
                                        Flux.just(
                                                new RowsEvent(rows), new ChartEvent(chartInference.infer(rows)))));
    }

    private Mono<Text2SqlResult> execute(String id, String dialect, SqlGeneration generation) {
        String safeSql = safetyValidator.validateAndNormalize(generation.sql());
        return sqlExecutor
                .execute(id, safeSql)
                .map(
                        result ->
                                new Text2SqlResult(
                                        id, dialect, safeSql, generation.explanation(), result, chartInference.infer(result)));
    }

    private Mono<String> schema(String id, String question) {
        return Mono.fromCallable(() -> schemaProvider.renderForPrompt(id, question))
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
