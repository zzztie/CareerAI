package com.zzztie.careerai.service;

import com.zzztie.careerai.config.RagImportQueueConfig;
import com.zzztie.careerai.dto.RagImportTask;
import com.zzztie.careerai.repository.RagImportTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagImportSubmitServiceTest {

    @TempDir
    Path storage;

    @Test
    void savesFileAndTaskBeforePublishingOnlyItsId() throws Exception {
        RagImportTaskRepository tasks = mock(RagImportTaskRepository.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(call -> {
            assertTrue(Files.exists(Path.of(call.getArgument(3, String.class))));
            return null;
        }).when(tasks).create(any(UUID.class), anyString(), anyString(), anyString());
        when(tasks.findById(any(UUID.class))).thenAnswer(call -> {
            UUID id = call.getArgument(0);
            return Optional.of(new RagImportTask(id, "knowledge:upload:" + id,
                    "guide.txt", storage.resolve(id + ".txt").toString(),
                    RagImportTask.Status.PENDING, 0, null,
                    Instant.now(), Instant.now()));
        });
        MockMultipartFile file = new MockMultipartFile(
                "file", "guide.txt", "text/plain",
                "Java 基础".getBytes(StandardCharsets.UTF_8));

        RagImportTask task = new RagImportSubmitService(
                tasks, rabbit, storage.toString()).submit(file);

        Path storedFile = storage.resolve(task.id() + ".txt");
        assertTrue(Files.exists(storedFile));
        assertEquals("Java 基础", Files.readString(storedFile));
        var order = inOrder(tasks, rabbit);
        order.verify(tasks).create(eq(task.id()), eq(task.documentKey()),
                eq("guide.txt"), eq(storedFile.toString()));
        order.verify(rabbit).convertAndSend(
                RagImportQueueConfig.QUEUE_NAME, task.id().toString());
    }

    @Test
    void databaseFailureDeletesStagedFileAndNeverPublishes() throws Exception {
        RagImportTaskRepository tasks = mock(RagImportTaskRepository.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(tasks).create(any(UUID.class), anyString(), anyString(), anyString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "guide.txt", "text/plain", "test".getBytes());

        assertThrows(IllegalStateException.class, () ->
                new RagImportSubmitService(tasks, rabbit, storage.toString())
                        .submit(file));

        try (var files = Files.list(storage)) {
            assertEquals(0, files.count());
        }
        verify(rabbit, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void retriesFailedTaskUsingItsExistingFileAndId() throws Exception {
        UUID id = UUID.randomUUID();
        Path file = storage.resolve(id + ".txt");
        Files.writeString(file, "Java 基础");
        RagImportTask failed = new RagImportTask(id, "knowledge:upload:" + id,
                "guide.txt", file.toString(), RagImportTask.Status.FAILED, 1,
                "上次处理失败", Instant.now(), Instant.now());
        RagImportTask pending = new RagImportTask(id, failed.documentKey(),
                failed.originalFilename(), failed.storedPath(),
                RagImportTask.Status.PENDING, 1, null,
                failed.createdAt(), Instant.now());
        RagImportTaskRepository tasks = mock(RagImportTaskRepository.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        when(tasks.findById(id)).thenReturn(Optional.of(failed), Optional.of(pending));
        when(tasks.resetFailedForRetry(id)).thenReturn(true);

        RagImportTask result = new RagImportSubmitService(
                tasks, rabbit, storage.toString()).retry(id);

        assertEquals(RagImportTask.Status.PENDING, result.status());
        assertEquals(id, result.id());
        verify(tasks).resetFailedForRetry(id);
        verify(rabbit).convertAndSend(RagImportQueueConfig.QUEUE_NAME, id.toString());
        verify(tasks, never()).create(any(UUID.class), anyString(), anyString(), anyString());
        assertEquals("Java 基础", Files.readString(file));
    }

    @Test
    void doesNotPublishWhenAnotherRequestAlreadyChangedFailedStatus() throws Exception {
        UUID id = UUID.randomUUID();
        Path file = storage.resolve(id + ".txt");
        Files.writeString(file, "Java 基础");
        RagImportTaskRepository tasks = mock(RagImportTaskRepository.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        when(tasks.findById(id)).thenReturn(Optional.of(new RagImportTask(
                id, "knowledge:upload:" + id, "guide.txt", file.toString(),
                RagImportTask.Status.FAILED, 1, "上次处理失败",
                Instant.now(), Instant.now())));
        when(tasks.resetFailedForRetry(id)).thenReturn(false);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> new RagImportSubmitService(tasks, rabbit, storage.toString())
                        .retry(id));

        verify(rabbit, never()).convertAndSend(anyString(), anyString());
    }
}
