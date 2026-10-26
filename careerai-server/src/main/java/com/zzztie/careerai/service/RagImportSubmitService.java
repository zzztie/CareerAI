package com.zzztie.careerai.service;

import com.zzztie.careerai.config.RagImportQueueConfig;
import com.zzztie.careerai.dto.RagImportTask;
import com.zzztie.careerai.repository.RagImportTaskRepository;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * 导入任务的生产者：先保存原文件和任务记录，再只把任务 ID 发到队列。
 * 队列发送失败时保留原文件并标记失败，用户可用同一任务 ID 重试。
 */
@Service
@ConditionalOnProperty(name = "careerai.import.enabled", havingValue = "true")
public class RagImportSubmitService {

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private final RagImportTaskRepository tasks;
    private final RabbitTemplate rabbitTemplate;
    private final Path storageDirectory;

    public RagImportSubmitService(
            RagImportTaskRepository tasks,
            RabbitTemplate rabbitTemplate,
            @Value("${careerai.import.storage-dir:uploads/knowledge}") String storageDirectory
    ) {
        this.tasks = tasks;
        this.rabbitTemplate = rabbitTemplate;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public RagImportTask submit(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "文档不能为空，且不能超过 5 MB");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件名不能为空");
        }
        filename = Path.of(filename.replace('\\', '/')).getFileName().toString();
        if (filename.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件名过长");
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        String extension;
        if (lower.endsWith(".pdf")) {
            extension = ".pdf";
        } else if (lower.endsWith(".txt")) {
            extension = ".txt";
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "目前只支持 PDF 和 UTF-8 TXT 文档");
        }

        UUID id = UUID.randomUUID();
        String documentKey = "knowledge:upload:" + id;
        Path storedPath = storageDirectory.resolve(id + extension);
        // 原文件先落盘；消费者稍后根据任务记录定位它，消息里不传文件内容。
        try {
            Files.createDirectories(storageDirectory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, storedPath);
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "保存上传文档失败", e);
        }

        // 若任务记录创建失败，删除刚保存的文件，避免留下没有任务 ID 的孤儿文件。
        try {
            tasks.create(id, documentKey, filename, storedPath.toString());
        } catch (RuntimeException e) {
            try {
                Files.deleteIfExists(storedPath);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }

        // 队列只传任务 ID；文件及进度都由持久化存储负责。
        try {
            rabbitTemplate.convertAndSend(RagImportQueueConfig.QUEUE_NAME, id.toString());
        } catch (AmqpException e) {
            tasks.markFailed(id, "消息队列暂时不可用；任务尚未进入处理队列。");
        }
        return tasks.findById(id).orElseThrow();
    }

    /** 仅失败且原文件仍存在的任务可重试；状态转换防止并发重复投递。 */
    public RagImportTask retry(UUID id) {
        RagImportTask task = tasks.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到导入任务"));
        if (task.status() != RagImportTask.Status.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有失败的任务可以重试");
        }
        Path path = Path.of(task.storedPath()).toAbsolutePath().normalize();
        if (!path.startsWith(storageDirectory) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "原文档已不存在，无法重试；请重新上传");
        }
        if (!tasks.resetFailedForRetry(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务状态已变化，请重新查询");
        }
        try {
            rabbitTemplate.convertAndSend(RagImportQueueConfig.QUEUE_NAME, id.toString());
        } catch (AmqpException e) {
            tasks.markFailed(id, "消息队列暂时不可用；重试任务尚未进入处理队列。");
        }
        return tasks.findById(id).orElseThrow();
    }
}
