package com.zzztie.careerai.service;

import com.zzztie.careerai.dto.DocumentChunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.main.lazy-initialization=true",
        "spring.ai.deepseek.api-key=test-only",
        "spring.datasource.username=test-only",
        "spring.datasource.password=test-only"
})
class OllamaEmbeddingIntegrationTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    void shouldEmbedDocumentChunk() {
        DocumentChunk chunk = new DocumentChunk(
                "resume.md", 1, "熟悉 Java 和 Spring Boot 开发"
        );

        float[] vector = embeddingModel.embed(chunk.content());

        assertEquals(1024, vector.length);
        System.out.printf("来源=%s，块=%d，向量维度=%d%n",
                chunk.source(), chunk.index(), vector.length);
    }
}