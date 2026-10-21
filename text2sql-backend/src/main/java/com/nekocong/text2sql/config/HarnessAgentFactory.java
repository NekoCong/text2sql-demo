package com.nekocong.text2sql.config;

import com.nekocong.text2sql.prompt.PromptCatalog;
import com.nekocong.text2sql.tool.SqlTools;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import java.nio.file.Paths;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates {@link HarnessAgent} instances on demand.
 *
 * <p>{@code HarnessAgent} (like the underlying {@code ReActAgent}) is not safe for concurrent
 * calls, so web requests build a fresh agent per request. The factory is intentionally stateless
 * apart from immutable configuration.
 *
 * <p>The agent is equipped with the text-to-SQL tools ({@link SqlTools}) so it can answer data
 * questions through {@code query_database}; the shell tool is disabled as it is irrelevant and a
 * needless security surface for a data agent.
 */
@Component
public class HarnessAgentFactory {

    private final AgentProperties properties;
    private final SqlTools sqlTools;
    private final PromptCatalog prompts;

    /**
     * Creates the factory.
     *
     * @param properties agent configuration
     * @param sqlTools the text-to-SQL tools registered on the agent
     * @param prompts the prompt catalog (loads the system prompt from {@code prompts/})
     */
    public HarnessAgentFactory(AgentProperties properties, SqlTools sqlTools, PromptCatalog prompts) {
        this.properties = properties;
        this.sqlTools = sqlTools;
        this.prompts = prompts;
    }

    /**
     * Builds a new agent from the configured properties, using the given model.
     *
     * @param model the model to use (typically the user's configured model)
     * @return a ready-to-use {@link HarnessAgent}
     */
    public HarnessAgent create(Model model) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(sqlTools);
        HarnessAgent.Builder builder =
                HarnessAgent.builder()
                        .name(properties.getName())
                        .sysPrompt(resolveSysPrompt())
                        .model(model)
                        .workspace(Paths.get(properties.getWorkspace()))
                        .maxContextTokens(properties.getMaxContextTokens())
                        .toolkit(toolkit)
                        .disableShellTool();
        if (properties.isSubagentsEnabled()) {
            builder.subagents(subagentDeclarations());
        }
        return builder.build();
    }

    /**
     * Resolves the agent system prompt: the inline override when set, otherwise the prompt file
     * ({@code text2sql.agent.sys-prompt-file}).
     *
     * @return the system prompt
     */
    private String resolveSysPrompt() {
        return StringUtils.hasText(properties.getSysPrompt())
                ? properties.getSysPrompt()
                : prompts.render(properties.getSysPromptFile());
    }

    /**
     * Declares the multi-agent team the parent agent may delegate to (DB-GPT-style planner/executor/
     * reporter split). Exposed separately so it can be verified without a model key or agent build.
     *
     * @return the subagent declarations
     */
    public List<SubagentDeclaration> subagentDeclarations() {
        return List.of(
                SubagentDeclaration.builder()
                        .name("schema_explorer")
                        .description("Explore the database schema (tables and columns).")
                        .inlineAgentsBody(
                                "You explore a SQL database schema. Use list_tables and describe_table to "
                                        + "inspect the structure, then summarise the relevant tables concisely.")
                        .tools(List.of("list_tables", "describe_table"))
                        .build(),
                SubagentDeclaration.builder()
                        .name("sql_runner")
                        .description("Answer a data question by running a read-only SQL query.")
                        .inlineAgentsBody(
                                "You answer data questions. Call query_database with the user's question and "
                                        + "report the returned SQL plus a short interpretation of the rows.")
                        .tools(List.of("query_database"))
                        .build(),
                SubagentDeclaration.builder()
                        .name("report_writer")
                        .description("Turn query results into a short, structured written report.")
                        .inlineAgentsBody(
                                "You write clear, concise reports from provided data. Do not invent numbers; "
                                        + "base every statement on the supplied SQL results.")
                        .build());
    }
}
