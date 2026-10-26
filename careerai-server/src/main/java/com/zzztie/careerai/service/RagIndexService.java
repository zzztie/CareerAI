package com.zzztie.careerai.service;

import com.zzztie.careerai.dto.DocumentChunk;
import com.zzztie.careerai.repository.RagChunkRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 负责文本向量化与 PostgreSQL 索引更新。重建时先完成外部 Embedding 调用，
 * 再在一笔数据库事务中替换旧块，避免向量服务失败后丢失可检索的旧版本。
 */
@Service
public class RagIndexService {

    private final EmbeddingModel embeddingModel;
    private final RagChunkRepository repository;
    private final TransactionTemplate ragTransaction;

    public RagIndexService(EmbeddingModel embeddingModel,
                           RagChunkRepository repository,
                           @Qualifier("ragDataSource") DataSource ragDataSource) {
        this.embeddingModel = embeddingModel;
        this.repository = repository;
        this.ragTransaction = new TransactionTemplate(
                new DataSourceTransactionManager(ragDataSource)
        );
    }

    public void indexChunk(String documentKey, DocumentChunk chunk) {
        float[] vector = embeddingModel.embed(chunk.content());
        repository.save(documentKey, chunk, vector);
    }

    public void rebuildDocument(String documentKey, List<DocumentChunk> chunks) {
        if (documentKey == null || documentKey.isBlank()) {
            throw new IllegalArgumentException("documentKey 不能为空");
        }
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("文档至少需要一个 Chunk");
        }

        // Embedding 是外部网络调用，不放进数据库事务；全部成功后才开始替换。
        List<float[]> vectors = new ArrayList<>(chunks.size());
        for (DocumentChunk chunk : chunks) {
            vectors.add(embeddingModel.embed(chunk.content()));
        }

        // 删除旧块与写入新块同属一笔 PostgreSQL 事务，数据库失败时整体回滚。
        ragTransaction.executeWithoutResult(status -> {
            repository.deleteByDocumentKey(documentKey);
            for (int i = 0; i < chunks.size(); i++) {
                repository.save(documentKey, chunks.get(i), vectors.get(i));
            }
        });
    }

    public Optional<DocumentChunk> searchNearest(
            String documentKey, String question
    ) {
        if (documentKey == null || documentKey.isBlank()) {
            throw new IllegalArgumentException("documentKey 不能为空");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }

        float[] queryVector = embeddingModel.embed(question);
        return repository.findNearest(documentKey, queryVector);
    }

    public List<DocumentChunk> searchTopK(
            String documentKey, String question, int k
    ) {
        if (documentKey == null || documentKey.isBlank()) {
            throw new IllegalArgumentException("documentKey 不能为空");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k 必须大于 0");
        }

        float[] queryVector = embeddingModel.embed(question);
        return repository.findTopK(documentKey, queryVector, k);
    }

    public List<DocumentChunk> searchTopKWithinDistance(
            String documentKey, String question, int k, double maxDistance
    ) {
        if (documentKey == null || documentKey.isBlank()) {
            throw new IllegalArgumentException("documentKey 不能为空");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k 必须大于 0");
        }
        if (!Double.isFinite(maxDistance) || maxDistance < 0) {
            throw new IllegalArgumentException("最大距离必须是非负有限数字");
        }

        float[] queryVector = embeddingModel.embed(question);
        return repository.findTopKWithinDistance(
                documentKey, queryVector, k, maxDistance
        );
    }
}
