package com.nekocong.text2sql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the in-memory knowledge base, bound from {@code text2sql.knowledge.*}.
 */
@ConfigurationProperties(prefix = "text2sql.knowledge")
public class KnowledgeProperties {

    /** Maximum chunk size, in characters. */
    private int chunkSize = 800;

    /** Default number of chunks returned by a search. */
    private int topK = 5;

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }
}
