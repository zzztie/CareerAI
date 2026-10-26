package com.zzztie.careerai.service;

import com.zzztie.careerai.config.RagImportQueueConfig;
import com.zzztie.careerai.dto.DocumentChunk;
import com.zzztie.careerai.dto.RagImportTask;
import com.zzztie.careerai.repository.RagImportTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 消费任务 ID 后从持久化记录读取原文件，完成切块和索引。
 * 消息可重复投递，任务状态负责阻止已完成任务再次处理。
 */
@Service
@ConditionalOnProperty(name = "careerai.import.enabled", havingValue = "true")
public class RagImportConsumer {

    private static final Logger log = LoggerFactory.getLogger(RagImportConsumer.class);

    private final RagImportTaskRepository tasks;
    private final ResumePdfTextExtractor pdfExtractor;
    private final RagIndexService indexService;
    private final PlainTextReader textReader = new PlainTextReader();
    private final TextChunker chunker = new TextChunker();
    private final Path storageDirectory;

    public RagImportConsumer(
            RagImportTaskRepository tasks,
            ResumePdfTextExtractor pdfExtractor,
            RagIndexService indexService,
            @Value("${careerai.import.storage-dir:uploads/knowledge}") String storageDirectory
    ) {
        this.tasks = tasks;
        this.pdfExtractor = pdfExtractor;
        this.indexService = indexService;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @RabbitListener(queues = RagImportQueueConfig.QUEUE_NAME, concurrency = "1")
    public void process(String taskIdText) {
        UUID taskId;
        try {
            taskId = UUID.fromString(taskIdText);
        } catch (IllegalArgumentException e) {
            log.warn("event=rag_import.invalid_task_id");
            return;
        }

        Optional<RagImportTask> found = tasks.findById(taskId);
        if (found.isEmpty()) {
            log.warn("event=rag_import.task_missing taskId={}", taskId);
            return;
        }
        RagImportTask task = found.get();
        if (task.status() == RagImportTask.Status.SUCCEEDED) {
            log.info("event=rag_import.duplicate_completed taskId={}", taskId);
            return;
        }
        // 只有成功抢到状态转换的消费者才能继续；重复消息在这里退出。
        if (!tasks.markProcessing(taskId)) {
            log.info("event=rag_import.task_not_started taskId={}", taskId);
            return;
        }

        log.info("event=rag_import.processing taskId={} attempt={}", taskId, task.attempts() + 1);
        try {
            String text = readText(task);
            List<DocumentChunk> chunks = chunker.splitByParagraph(
                    task.originalFilename(), text, 600
            );
            indexService.rebuildDocument(task.documentKey(), chunks);
            if (!tasks.markSucceeded(taskId)) {
                throw new IllegalStateException("任务完成时状态已变化");
            }
            log.info("event=rag_import.completed taskId={} chunks={}", taskId, chunks.size());
        } catch (IOException | RuntimeException e) {
            // 对外只返回固定的安全原因；异常消息可能包含文件路径或下游服务细节。
            log.warn("event=rag_import.failed taskId={} exceptionType={}",
                    taskId, e.getClass().getSimpleName());
            tasks.markFailed(taskId, safeReason(e));
        }
    }

    private String readText(RagImportTask task) throws IOException {
        Path path = Path.of(task.storedPath()).toAbsolutePath().normalize();
        if (!path.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("任务文件路径不在导入目录中");
        }
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String text;
        if (lower.endsWith(".pdf")) {
            text = pdfExtractor.extractText(Files.readAllBytes(path));
        } else if (lower.endsWith(".txt")) {
            text = textReader.read(path);
        } else {
            throw new IllegalArgumentException("任务文件类型不受支持");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("文档中没有可索引的文本");
        }
        return text;
    }

    private static String safeReason(Exception e) {
        if (e instanceof IOException) {
            return "保存的文档无法读取；请检查文件是否仍然存在。";
        }
        if (e instanceof IllegalArgumentException) {
            return "文档内容无法解析或没有可索引的文本。";
        }
        return "Embedding 或索引服务暂时失败；可稍后重试。";
    }
}
