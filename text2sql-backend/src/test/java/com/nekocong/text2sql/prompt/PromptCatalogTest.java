package com.nekocong.text2sql.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.nekocong.text2sql.nl2sql.PromptAssembler;
import org.junit.jupiter.api.Test;

/**
 * Verifies prompt templates load from {@code resources/prompts/} and render with placeholders.
 */
class PromptCatalogTest {

    private final PromptCatalog catalog = new PromptCatalog();
    private final PromptAssembler assembler = new PromptAssembler(catalog);

    @Test
    void rendersNl2SqlSystemPrompt() {
        String prompt = assembler.systemPrompt("H2");
        assertThat(prompt).contains("H2").contains("JSON");
        // The final answer must be Chinese.
        assertThat(prompt).contains("简体中文");
        assertThat(prompt).doesNotContain("{{dialect}}");
    }

    @Test
    void rendersUserPromptWithPlaceholders() {
        String prompt = assembler.userPrompt("CREATE TABLE t (id INT)", "统计订单数量", "MYSQL");
        assertThat(prompt).contains("CREATE TABLE t (id INT)").contains("统计订单数量").contains("MYSQL");
        assertThat(prompt).doesNotContain("{{");
    }

    @Test
    void loadsAgentSystemPrompt() {
        String prompt = catalog.render("prompts/agent-system.md");
        assertThat(prompt).contains("query_database").contains("简体中文");
    }
}
