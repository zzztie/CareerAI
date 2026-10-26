package com.zzztie.careerai.repository;
import java.util.Optional;
import com.pgvector.PGvector;
import com.zzztie.careerai.dto.DocumentChunk;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import javax.sql.DataSource;

@Repository
public class RagChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    public RagChunkRepository(@Qualifier("ragDataSource") DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void save(String documentKey, DocumentChunk chunk, float[] vector) {
        if (documentKey == null || documentKey.isBlank()) {
            throw new IllegalArgumentException("documentKey 不能为空");
        }
        if (vector == null || vector.length != 1024) {
            throw new IllegalArgumentException("向量必须为 1024 维");
        }

        jdbcTemplate.update("""
                INSERT INTO rag_chunk
                    (document_key, chunk_index, source, content, embedding)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (document_key, chunk_index)
                DO UPDATE SET
                    source = EXCLUDED.source,
                    content = EXCLUDED.content,
                    embedding = EXCLUDED.embedding
                """,
                documentKey, chunk.index(), chunk.source(),
                chunk.content(), new PGvector(vector));
    }

    public int deleteByDocumentKey(String documentKey) {
        if (documentKey == null || documentKey.isBlank()) {
            throw new IllegalArgumentException("documentKey 不能为空");
        }
        return jdbcTemplate.update(
                "DELETE FROM rag_chunk WHERE document_key = ?", documentKey
        );
    }

    public Optional<DocumentChunk> findNearest(
            String documentKey, float[] queryVector
    ) {
        if (documentKey == null || documentKey.isBlank()) {
            throw new IllegalArgumentException("documentKey 不能为空");
        }
        if (queryVector == null || queryVector.length != 1024) {
            throw new IllegalArgumentException("查询向量必须为 1024 维");
        }

        return jdbcTemplate.query("""
            SELECT source, chunk_index, content
            FROM rag_chunk
            WHERE document_key = ?
            ORDER BY embedding <=> ?
            LIMIT 1
            """,
                (rs, rowNum) -> new DocumentChunk(
                        rs.getString("source"),
                        rs.getInt("chunk_index"),
                        rs.getString("content")
                ),
                documentKey, new PGvector(queryVector)
        ).stream().findFirst();
    }

    public List<DocumentChunk> findTopK(
            String documentKey, float[] queryVector, int k
    ) {
        if (documentKey == null || documentKey.isBlank()) {
            throw new IllegalArgumentException("documentKey 不能为空");
        }
        if (queryVector == null || queryVector.length != 1024) {
            throw new IllegalArgumentException("查询向量必须为 1024 维");
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k 必须大于 0");
        }

        return jdbcTemplate.query("""
            SELECT source, chunk_index, content
            FROM rag_chunk
            WHERE document_key = ?
            ORDER BY embedding <=> ?
            LIMIT ?
            """,
                (rs, rowNum) -> new DocumentChunk(
                        rs.getString("source"),
                        rs.getInt("chunk_index"),
                        rs.getString("content")
                ),
                documentKey, new PGvector(queryVector), k
        );
    }

    public List<DocumentChunk> findTopKWithinDistance(
            String documentKey, float[] queryVector, int k, double maxDistance
    ) {
        if (documentKey == null || documentKey.isBlank()) {
            throw new IllegalArgumentException("documentKey 不能为空");
        }
        if (queryVector == null || queryVector.length != 1024) {
            throw new IllegalArgumentException("查询向量必须为 1024 维");
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k 必须大于 0");
        }
        if (!Double.isFinite(maxDistance) || maxDistance < 0) {
            throw new IllegalArgumentException("最大距离必须是非负有限数字");
        }

        return jdbcTemplate.query("""
            SELECT source, chunk_index, content
            FROM rag_chunk
            WHERE document_key = ?
              AND (embedding <=> ?) <= ?
            ORDER BY embedding <=> ?
            LIMIT ?
            """,
                (rs, rowNum) -> new DocumentChunk(
                        rs.getString("source"),
                        rs.getInt("chunk_index"),
                        rs.getString("content")
                ),
                documentKey,
                new PGvector(queryVector),
                maxDistance,
                new PGvector(queryVector),
                k
        );
    }

}