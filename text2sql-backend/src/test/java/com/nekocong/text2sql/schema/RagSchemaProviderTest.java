package com.nekocong.text2sql.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.nekocong.text2sql.config.Nl2SqlProperties;
import com.nekocong.text2sql.config.RagProperties;
import com.nekocong.text2sql.datasource.DataSourceRegistry;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;

/**
 * Verifies embedding-based schema retrieval: relevance selection + foreign-key neighbour expansion.
 * Uses a deterministic in-test embedding model, so no embedding API key is required. Constructed
 * directly (the bean is only active when {@code text2sql.rag.enabled=true}).
 */
@SpringBootTest
class RagSchemaProviderTest {

    @Autowired
    private DataSourceRegistry registry;

    @Autowired
    private SchemaIntrospector introspector;

    @Autowired
    private SchemaRenderer renderer;

    /** Maps text to a one-hot vector over the four demo tables (word-boundary matched). */
    private static final class TableTokenEmbedding implements EmbeddingModel {
        private static final String[] TABLES = {"orders", "products", "customers", "order_items"};

        @Override
        public Mono<double[]> embed(ContentBlock block) {
            String text = block instanceof TextBlock textBlock ? textBlock.getText() : "";
            Set<String> tokens =
                    new HashSet<>(Arrays.asList(text.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+")));
            double[] vector = new double[TABLES.length];
            for (int i = 0; i < TABLES.length; i++) {
                vector[i] = tokens.contains(TABLES[i]) ? 1.0 : 0.0;
            }
            return Mono.just(vector);
        }

        @Override
        public String getModelName() {
            return "test-table-token";
        }

        @Override
        public int getDimensions() {
            return TABLES.length;
        }
    }

    private RagSchemaProvider provider() {
        RagProperties rag = new RagProperties();
        rag.setTopK(1);
        rag.setScoreThreshold(0.0);
        Nl2SqlProperties nl2Sql = new Nl2SqlProperties();
        nl2Sql.setSchemaCharBudget(100000);
        return new RagSchemaProvider(
                new TableTokenEmbedding(), registry, introspector, renderer, rag, nl2Sql);
    }

    @Test
    void selectsRelevantTableAndExpandsForeignKeyNeighbours() {
        String ddl = provider().renderForPrompt(null, "how many orders are there");

        // 'orders' is the top match; its FK neighbours (customers, order_items) are pulled in...
        assertThat(ddl).containsIgnoringCase("CREATE TABLE orders");
        assertThat(ddl).containsIgnoringCase("CREATE TABLE customers");
        assertThat(ddl).containsIgnoringCase("CREATE TABLE order_items");
        // ...but an unrelated table is left out.
        assertThat(ddl).doesNotContainIgnoringCase("CREATE TABLE products");
    }

    @Test
    void failsSafeToFullSchemaWhenEmbeddingErrors() {
        EmbeddingModel broken =
                new EmbeddingModel() {
                    @Override
                    public Mono<double[]> embed(ContentBlock block) {
                        return Mono.error(new IllegalStateException("no embedding"));
                    }

                    @Override
                    public String getModelName() {
                        return "broken";
                    }

                    @Override
                    public int getDimensions() {
                        return 1;
                    }
                };
        RagProperties rag = new RagProperties();
        Nl2SqlProperties nl2Sql = new Nl2SqlProperties();
        nl2Sql.setSchemaCharBudget(100000);
        RagSchemaProvider provider =
                new RagSchemaProvider(broken, registry, introspector, renderer, rag, nl2Sql);

        String ddl = provider.renderForPrompt(null, "anything");

        assertThat(ddl).containsIgnoringCase("CREATE TABLE orders");
        assertThat(ddl).containsIgnoringCase("CREATE TABLE products");
    }
}
