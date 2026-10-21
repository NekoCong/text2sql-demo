package com.nekocong.text2sql.schema;

/**
 * Supplies the database schema (as prompt-ready text) used to ground SQL generation.
 *
 * <p>Phase 1 ships {@link FullSchemaProvider} (whole small schema). Phase 2 adds a
 * retrieval/embedding implementation behind this same interface, so the pipeline never changes.
 */
public interface SchemaProvider {

    /**
     * Renders the schema that should ground SQL generation for a question.
     *
     * @param datasourceId the datasource id (or {@code null} for the default)
     * @param question the natural-language question
     * @return prompt-ready schema text
     */
    String renderForPrompt(String datasourceId, String question);
}
