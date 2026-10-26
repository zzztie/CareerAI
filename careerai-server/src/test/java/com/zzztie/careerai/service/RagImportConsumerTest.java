package com.zzztie.careerai.service;

import com.zzztie.careerai.dto.RagImportTask;
import com.zzztie.careerai.repository.RagImportTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagImportConsumerTest {

    @TempDir
    Path storage;

    private RagImportTask task(UUID id, Path path, RagImportTask.Status status) {
        return new RagImportTask(id, "knowledge:upload:" + id,
                "guide.txt", path.toString(), status, 0, null,
                Instant.now(), Instant.now());
    }

    @Test
    void indexesTextAndMarksTaskSucceeded() throws Exception {
        UUID id = UUID.randomUUID();
        Path file = storage.resolve(id + ".txt");
        Files.writeString(file, "Java 基础。\n\nSpring Boot 接口。");
        RagImportTaskRepository tasks = mock(RagImportTaskRepository.class);
        RagIndexService index = mock(RagIndexService.class);
        when(tasks.findById(id)).thenReturn(Optional.of(
                task(id, file, RagImportTask.Status.PENDING)));
        when(tasks.markProcessing(id)).thenReturn(true);
        when(tasks.markSucceeded(id)).thenReturn(true);

        new RagImportConsumer(tasks, mock(ResumePdfTextExtractor.class),
                index, storage.toString()).process(id.toString());

        verify(index).rebuildDocument(eq("knowledge:upload:" + id),
                org.mockito.ArgumentMatchers.argThat(chunks ->
                        chunks.size() == 2
                                && chunks.get(0).content().contains("Java 基础")
                                && chunks.get(1).content().contains("Spring Boot")));
        verify(tasks).markSucceeded(id);
        verify(tasks, never()).markFailed(eq(id), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void blankTextBecomesFailedWithoutWritingIndex() throws Exception {
        UUID id = UUID.randomUUID();
        Path file = storage.resolve(id + ".txt");
        Files.writeString(file, " \n ");
        RagImportTaskRepository tasks = mock(RagImportTaskRepository.class);
        RagIndexService index = mock(RagIndexService.class);
        when(tasks.findById(id)).thenReturn(Optional.of(
                task(id, file, RagImportTask.Status.PENDING)));
        when(tasks.markProcessing(id)).thenReturn(true);

        new RagImportConsumer(tasks, mock(ResumePdfTextExtractor.class),
                index, storage.toString()).process(id.toString());

        verify(tasks).markFailed(eq(id), contains("没有可索引的文本"));
        verifyNoInteractions(index);
    }

    @Test
    void redeliveredCompletedTaskDoesNotReindex() {
        UUID id = UUID.randomUUID();
        RagImportTaskRepository tasks = mock(RagImportTaskRepository.class);
        RagIndexService index = mock(RagIndexService.class);
        when(tasks.findById(id)).thenReturn(Optional.of(
                task(id, storage.resolve(id + ".txt"), RagImportTask.Status.SUCCEEDED)));

        new RagImportConsumer(tasks, mock(ResumePdfTextExtractor.class),
                index, storage.toString()).process(id.toString());

        verify(tasks, never()).markProcessing(id);
        verifyNoInteractions(index);
    }
}
