CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS rag_chunk (
    document_key VARCHAR(200) NOT NULL,
    chunk_index INTEGER NOT NULL,
    source TEXT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1024) NOT NULL,
    PRIMARY KEY (document_key, chunk_index)
);
