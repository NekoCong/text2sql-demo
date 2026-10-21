package com.nekocong.text2sql.nl2sql;

import com.nekocong.text2sql.prompt.PromptCatalog;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the system/user prompts that turn a schema + question into a JSON SQL response.
 *
 * <p>Templates live in {@code resources/prompts/} and are rendered by {@link PromptCatalog}.
 */
@Component
public class PromptAssembler {

    private static final String SYSTEM_LOCATION = "prompts/nl2sql-system.md";
    private static final String USER_LOCATION = "prompts/nl2sql-user.md";

    private final PromptCatalog prompts;

    /**
     * Creates the assembler.
     *
     * @param prompts the prompt catalog
     */
    public PromptAssembler(PromptCatalog prompts) {
        this.prompts = prompts;
    }

    /**
     * The system prompt steering the model to emit a single JSON object (with a Chinese explanation).
     *
     * @param dialect the SQL dialect name (e.g. {@code MYSQL})
     * @return the system prompt
     */
    public String systemPrompt(String dialect) {
        return prompts.render(SYSTEM_LOCATION, Map.of("dialect", dialect));
    }

    /**
     * The user prompt carrying the schema and the question.
     *
     * @param schema the prompt-ready schema DDL
     * @param question the natural-language question
     * @param dialect the SQL dialect name
     * @return the user prompt
     */
    public String userPrompt(String schema, String question, String dialect) {
        return prompts.render(USER_LOCATION, Map.of("schema", schema, "question", question, "dialect", dialect));
    }
}
