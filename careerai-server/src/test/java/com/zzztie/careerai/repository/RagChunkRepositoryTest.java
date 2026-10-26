package com.zzztie.careerai.repository;

import com.zzztie.careerai.dto.DocumentChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.main.lazy-initialization=true",
        "spring.ai.deepseek.api-key=test-only",
        "spring.datasource.username=test-only",
        "spring.datasource.password=test-only"
})
class RagChunkRepositoryTest {

    @Autowired
    private RagChunkRepository repository;

    @Autowired
    @Qualifier("ragDataSource")
    private DataSource ragDataSource;

    @Test
    void savesChunkAndVector() {
        String documentKey = "u43-test-" + UUID.randomUUID();
        DocumentChunk chunk = new DocumentChunk(
                "resume.txt", 1, "熟悉 Java 和 Spring Boot 开发"
        );
        float[] vector = new float[1024];
        vector[0] = 1.0f;

        JdbcTemplate jdbc = new JdbcTemplate(ragDataSource);
        try {
            repository.save(documentKey, chunk, vector);

            String savedContent = jdbc.queryForObject(
                    "SELECT content FROM rag_chunk WHERE document_key = ? AND chunk_index = ?",
                    String.class, documentKey, chunk.index()
            );
            Integer dimensions = jdbc.queryForObject(
                    "SELECT vector_dims(embedding) FROM rag_chunk WHERE document_key = ? AND chunk_index = ?",
                    Integer.class, documentKey, chunk.index()
            );

            assertEquals(chunk.content(), savedContent);
            assertEquals(1024, dimensions);
        } finally {
            jdbc.update("DELETE FROM rag_chunk WHERE document_key = ?", documentKey);
        }
    }

    @Test
    void deletesChunksForOneDocument() {
        String documentKey = "u43-delete-test-" + UUID.randomUUID();
        float[] vector = new float[1024];
        vector[0] = 1.0f;
        JdbcTemplate jdbc = new JdbcTemplate(ragDataSource);

        try {
            repository.save(
                    documentKey,
                    new DocumentChunk("resume.txt", 1, "旧版简历内容"),
                    vector
            );

            assertEquals(1, repository.deleteByDocumentKey(documentKey));

            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM rag_chunk WHERE document_key = ?",
                    Integer.class, documentKey
            );
            assertEquals(0, count);
        } finally {
            repository.deleteByDocumentKey(documentKey);
        }
    }

    @Test
    void findsNearestChunkByVector() {
        String documentKey = "u43-search-test-" + UUID.randomUUID();
        DocumentChunk javaChunk = new DocumentChunk("resume.txt", 1, "Java 后端开发");
        DocumentChunk uiChunk = new DocumentChunk("resume.txt", 2, "前端界面设计");

        float[] javaVector = new float[1024];
        javaVector[0] = 1.0f;
        float[] uiVector = new float[1024];
        uiVector[1] = 1.0f;
        float[] queryVector = new float[1024];
        queryVector[0] = 0.9f;
        queryVector[1] = 0.1f;

        try {
            repository.save(documentKey, javaChunk, javaVector);
            repository.save(documentKey, uiChunk, uiVector);

            assertEquals(
                    Optional.of(javaChunk),
                    repository.findNearest(documentKey, queryVector)
            );
        } finally {
            repository.deleteByDocumentKey(documentKey);
        }
    }

    @Test
    void findsTopKChunksInDistanceOrder() {
        String documentKey = "u44-topk-test-" + UUID.randomUUID();

        DocumentChunk javaChunk =
                new DocumentChunk("resume.txt", 1, "Java 后端开发");
        DocumentChunk redisChunk =
                new DocumentChunk("resume.txt", 2, "Redis 缓存实践");
        DocumentChunk designChunk =
                new DocumentChunk("resume.txt", 3, "平面设计");

        float[] javaVector = new float[1024];
        javaVector[0] = 1.0f;

        float[] redisVector = new float[1024];
        redisVector[0] = 1.0f;
        redisVector[1] = 1.0f;

        float[] designVector = new float[1024];
        designVector[1] = 1.0f;

        float[] queryVector = new float[1024];
        queryVector[0] = 1.0f;
        queryVector[1] = 0.2f;

        try {
            repository.save(documentKey, javaChunk, javaVector);
            repository.save(documentKey, redisChunk, redisVector);
            repository.save(documentKey, designChunk, designVector);

            assertEquals(
                    List.of(javaChunk, redisChunk),
                    repository.findTopK(documentKey, queryVector, 2)
            );
            assertEquals(
                    List.of(javaChunk),
                    repository.findTopK(documentKey, queryVector, 1)
            );
        } finally {
            repository.deleteByDocumentKey(documentKey);
        }
    }
    @Test
    void filtersByDocumentKeyAndDistance() {
        String resumeKey = "u44-resume-" + UUID.randomUUID();
        String jdKey = "u44-jd-" + UUID.randomUUID();

        DocumentChunk resumeChunk =
                new DocumentChunk("resume.txt", 1, "Java 后端开发经历");
        DocumentChunk unrelatedChunk =
                new DocumentChunk("resume.txt", 2, "平面设计经历");
        DocumentChunk jdChunk =
                new DocumentChunk("jd.txt", 1, "招聘 Java 后端工程师");

        float[] query = new float[1024];
        query[0] = 1.0f;

        float[] resumeVector = new float[1024];
        resumeVector[0] = 0.8f;
        resumeVector[1] = 0.6f;

        float[] unrelatedVector = new float[1024];
        unrelatedVector[1] = 1.0f;

        float[] jdVector = new float[1024];
        jdVector[0] = 1.0f;

        try {
            repository.save(resumeKey, resumeChunk, resumeVector);
            repository.save(resumeKey, unrelatedChunk, unrelatedVector);
            repository.save(jdKey, jdChunk, jdVector);

            assertEquals(
                    List.of(resumeChunk),
                    repository.findTopKWithinDistance(resumeKey, query, 3, 0.3)
            );
            assertEquals(
                    List.of(jdChunk),
                    repository.findTopKWithinDistance(jdKey, query, 3, 0.3)
            );
            assertEquals(
                    List.of(),
                    repository.findTopKWithinDistance(resumeKey, query, 3, 0.1)
            );
        } finally {
            repository.deleteByDocumentKey(resumeKey);
            repository.deleteByDocumentKey(jdKey);
        }
    }
}