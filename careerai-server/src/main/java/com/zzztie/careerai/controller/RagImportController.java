package com.zzztie.careerai.controller;

import com.zzztie.careerai.dto.RagImportTask;
import com.zzztie.careerai.repository.RagImportTaskRepository;
import com.zzztie.careerai.service.RagImportSubmitService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/ai/knowledge/import")
@ConditionalOnProperty(name = "careerai.import.enabled", havingValue = "true")
public class RagImportController {

    private final RagImportSubmitService submitService;
    private final RagImportTaskRepository tasks;
    private final String importKey;

    public RagImportController(
            RagImportSubmitService submitService,
            RagImportTaskRepository tasks,
            @Value("${CAREERAI_IMPORT_KEY:}") String importKey
    ) {
        this.submitService = submitService;
        this.tasks = tasks;
        this.importKey = importKey;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TaskView> upload(
            @RequestHeader(value = "X-Import-Key", required = false) String providedKey,
            @RequestParam("file") MultipartFile file
    ) {
        checkKey(providedKey);
        RagImportTask task = submitService.submit(file);
        HttpStatus responseStatus = task.status() == RagImportTask.Status.FAILED
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.ACCEPTED;
        return ResponseEntity.status(responseStatus).body(TaskView.from(task));
    }

    @GetMapping("/{id}")
    public TaskView status(
            @RequestHeader(value = "X-Import-Key", required = false) String providedKey,
            @PathVariable UUID id
    ) {
        checkKey(providedKey);
        RagImportTask task = tasks.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到导入任务"));
        return TaskView.from(task);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<TaskView> retry(
            @RequestHeader(value = "X-Import-Key", required = false) String providedKey,
            @PathVariable UUID id
    ) {
        checkKey(providedKey);
        RagImportTask task = submitService.retry(id);
        HttpStatus responseStatus = task.status() == RagImportTask.Status.FAILED
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.ACCEPTED;
        return ResponseEntity.status(responseStatus).body(TaskView.from(task));
    }

    private void checkKey(String providedKey) {
        if (importKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "文档导入密钥尚未配置");
        }
        if (providedKey == null || !MessageDigest.isEqual(
                importKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "文档导入密钥无效");
        }
    }

    public record TaskView(
            UUID taskId,
            RagImportTask.Status status,
            int attempts,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt
    ) {
        static TaskView from(RagImportTask task) {
            return new TaskView(task.id(), task.status(), task.attempts(),
                    task.errorMessage(), task.createdAt(), task.updatedAt());
        }
    }
}
