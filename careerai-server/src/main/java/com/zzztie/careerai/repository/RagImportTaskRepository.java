package com.zzztie.careerai.repository;

import com.zzztie.careerai.dto.RagImportTask;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RagImportTaskRepository {

    private final JdbcTemplate jdbc;

    public RagImportTaskRepository(@Qualifier("ragDataSource") DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void create(UUID id, String documentKey, String filename, String storedPath) {
        jdbc.update("""
                INSERT INTO rag_import_task
                    (id, document_key, original_filename, stored_path, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                """, id, documentKey, filename, storedPath);
    }

    public Optional<RagImportTask> findById(UUID id) {
        return jdbc.query("""
                SELECT id, document_key, original_filename, stored_path,
                       status, attempts, error_message, created_at, updated_at
                FROM rag_import_task WHERE id = ?
                """, rs -> rs.next() ? Optional.of(new RagImportTask(
                rs.getObject("id", UUID.class),
                rs.getString("document_key"),
                rs.getString("original_filename"),
                rs.getString("stored_path"),
                RagImportTask.Status.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                rs.getString("error_message"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        )) : Optional.empty(), id);
    }

    public boolean markProcessing(UUID id) {
        return jdbc.update("""
                UPDATE rag_import_task
                SET status = 'PROCESSING', attempts = attempts + 1,
                    error_message = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('PENDING', 'PROCESSING', 'FAILED')
                """, id) == 1;
    }

    public boolean resetFailedForRetry(UUID id) {
        return jdbc.update("""
                UPDATE rag_import_task
                SET status = 'PENDING', error_message = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'FAILED'
                """, id) == 1;
    }

    public boolean markSucceeded(UUID id) {
        return jdbc.update("""
                UPDATE rag_import_task
                SET status = 'SUCCEEDED', error_message = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PROCESSING'
                """, id) == 1;
    }

    public void markFailed(UUID id, String reason) {
        jdbc.update("""
                UPDATE rag_import_task
                SET status = 'FAILED', error_message = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('PENDING', 'PROCESSING')
                """, reason, id);
    }
}
