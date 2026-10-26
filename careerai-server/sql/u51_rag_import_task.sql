CREATE TABLE IF NOT EXISTS rag_import_task (
    id UUID PRIMARY KEY,
    document_key VARCHAR(200) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    stored_path TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT rag_import_task_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT rag_import_task_attempts_check CHECK (attempts >= 0)
);
