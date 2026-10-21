package com.nekocong.text2sql.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies the multi-agent team is declared, without building the agent (which would need a model
 * key).
 */
@SpringBootTest
class HarnessAgentFactorySubagentsTest {

    @Autowired
    private HarnessAgentFactory factory;

    @Test
    void declaresTheMultiAgentTeam() {
        List<SubagentDeclaration> declarations = factory.subagentDeclarations();

        assertThat(declarations)
                .extracting(SubagentDeclaration::getName)
                .containsExactly("schema_explorer", "sql_runner", "report_writer");
        assertThat(declarations).allSatisfy(d -> assertThat(d.getDescription()).isNotBlank());
    }
}
