package com.zzztie.careerai.service;

import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.zzztie.careerai.dto.DocumentChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.pgvector.PGvector;
import org.springframework.ai.embedding.EmbeddingModel;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.main.lazy-initialization=true",
        "spring.ai.deepseek.api-key=test-only",
        "spring.datasource.username=test-only",
        "spring.datasource.password=test-only"
})
class RagIndexServiceTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private RagIndexService indexService;

    @Autowired
    @Qualifier("ragDataSource")
    private DataSource ragDataSource;

    @Test
    void embedsAndSavesOneChunk() {
        String documentKey = "u43-index-test-" + UUID.randomUUID();
        DocumentChunk chunk = new DocumentChunk(
                "resume.txt", 1, "熟悉 Java 和 Spring Boot 开发"
        );
        JdbcTemplate jdbc = new JdbcTemplate(ragDataSource);

        try {
            indexService.indexChunk(documentKey, chunk);

            Map<String, Object> saved = jdbc.queryForMap("""
                    SELECT source, content, vector_dims(embedding) AS dimensions
                    FROM rag_chunk
                    WHERE document_key = ? AND chunk_index = ?
                    """, documentKey, chunk.index());

            assertEquals(chunk.source(), saved.get("source"));
            assertEquals(chunk.content(), saved.get("content"));
            assertEquals(1024, ((Number) saved.get("dimensions")).intValue());
        } finally {
            jdbc.update("DELETE FROM rag_chunk WHERE document_key = ?", documentKey);
        }
    }


    @Test
    void rebuildRemovesStaleChunks() {
        String documentKey = "u43-rebuild-test-" + UUID.randomUUID();
        JdbcTemplate jdbc = new JdbcTemplate(ragDataSource);
        String newContent = "新版简历：熟悉 Java 和 Spring Boot";

        try {
            indexService.rebuildDocument(documentKey, List.of(
                    new DocumentChunk("resume.txt", 1, "旧版简历第一块"),
                    new DocumentChunk("resume.txt", 2, "旧版简历第二块")
            ));

            indexService.rebuildDocument(documentKey, List.of(
                    new DocumentChunk("resume.txt", 1, newContent)
            ));

            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM rag_chunk WHERE document_key = ?",
                    Integer.class, documentKey
            );
            String savedContent = jdbc.queryForObject(
                    "SELECT content FROM rag_chunk WHERE document_key = ? AND chunk_index = 1",
                    String.class, documentKey
            );

            assertEquals(1, count);
            assertEquals(newContent, savedContent);
        } finally {
            jdbc.update("DELETE FROM rag_chunk WHERE document_key = ?", documentKey);
        }
    }

    @Test
    void retrievesChunkFromNaturalLanguageQuestion() {
        String documentKey = "u43-semantic-test-" + UUID.randomUUID();
        DocumentChunk backendChunk = new DocumentChunk(
                "resume.txt", 1, "使用 Java 与 Spring Boot 开发企业后台接口"
        );
        DocumentChunk designChunk = new DocumentChunk(
                "resume.txt", 2, "擅长平面海报设计、摄影和短视频剪辑"
        );
        JdbcTemplate jdbc = new JdbcTemplate(ragDataSource);

        try {
            indexService.rebuildDocument(
                    documentKey, List.of(backendChunk, designChunk)
            );

            DocumentChunk result = indexService.searchNearest(
                    documentKey, "谁适合开发网站的服务器端功能？"
            ).orElseThrow();

            System.out.println("命中的块序号：" + result.index());
            assertEquals(backendChunk, result);

            List<DocumentChunk> top2 = indexService.searchTopK(
                    documentKey,
                    "谁适合开发网站的服务器端功能？",
                    2
            );

            assertEquals(2, top2.size());

            for (int i = 0; i < top2.size(); i++) {
                DocumentChunk chunk = top2.get(i);
                System.out.printf(
                        "第 %d 名｜来源：%s｜块序号：%d｜原文：%s%n",
                        i + 1,
                        chunk.source(),
                        chunk.index(),
                        chunk.content()
                );
            }

            float[] questionVector =
                    embeddingModel.embed("谁适合开发网站的服务器端功能？");

            List<Map<String, Object>> rows = jdbc.queryForList("""
        WITH scored AS (
            SELECT chunk_index, source, content,
                   embedding <=> ? AS distance
            FROM rag_chunk
            WHERE document_key = ?
        )
        SELECT * FROM scored
        WHERE distance <= ?
        ORDER BY distance
        LIMIT ?
        """,
                    new PGvector(questionVector),
                    documentKey,
                    0.50,
                    2
            );

            for (Map<String, Object> row : rows) {
                System.out.printf(
                        "块 %s｜距离 %.4f｜原文：%s%n",
                        row.get("chunk_index"),
                        ((Number) row.get("distance")).doubleValue(),
                        row.get("content")
                );
            }

            List<DocumentChunk> filtered = indexService.searchTopKWithinDistance(
                    documentKey,
                    "谁适合开发网站的服务器端功能？",
                    2,
                    0.50
            );

            System.out.println("正式检索过滤后返回：" + filtered.size() + " 块");
            filtered.forEach(chunk ->
                    System.out.println("保留的原文：" + chunk.content())
            );

        } finally {
            jdbc.update("DELETE FROM rag_chunk WHERE document_key = ?", documentKey);
        }
    }

    @Test
    void rebuildKeepsOldVersionWhenNewWriteFails() {
        String documentKey = "u43-rollback-test-" + UUID.randomUUID();
        DocumentChunk oldChunk = new DocumentChunk(
                "resume.txt", 1, "旧版简历：Java 后端开发"
        );
        JdbcTemplate jdbc = new JdbcTemplate(ragDataSource);

        try {
            indexService.indexChunk(documentKey, oldChunk);

            assertThrows(DataIntegrityViolationException.class, () ->
                    indexService.rebuildDocument(documentKey, List.of(
                            new DocumentChunk("resume.txt", 1, "新版第一块"),
                            new DocumentChunk("resume.txt", 0, "序号错误的第二块")
                    ))
            );

            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM rag_chunk WHERE document_key = ?",
                    Integer.class, documentKey
            );
            String content = jdbc.queryForObject(
                    "SELECT content FROM rag_chunk WHERE document_key = ? AND chunk_index = 1",
                    String.class, documentKey
            );

            assertEquals(1, count);
            assertEquals(oldChunk.content(), content);
        } finally {
            jdbc.update("DELETE FROM rag_chunk WHERE document_key = ?", documentKey);
        }
    }
}