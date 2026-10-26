package com.zzztie.careerai.dto;

import java.time.Instant;
import java.util.UUID;

public record RagImportTask(
        UUID id,
        String documentKey,
        String originalFilename,
        String storedPath,
        Status status,
        int attempts,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status {
        PENDING, PROCESSING, SUCCEEDED, FAILED
    }
}
