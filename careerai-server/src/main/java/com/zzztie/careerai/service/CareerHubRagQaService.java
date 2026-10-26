package com.zzztie.careerai.service;

import com.zzztie.careerai.client.CareerHubJobClient;
import com.zzztie.careerai.client.CareerHubResumeClient;
import com.zzztie.careerai.dto.JobDTO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 指定岗位对比的编排层：从 CareerHub 取得授权简历和目标岗位，
 * 分别建立索引，再把三个来源交给 RAG 问答服务生成有出处的回答。
 */
@Service
public class CareerHubRagQaService {

    private static final String KNOWLEDGE_DOCUMENT_KEY =
            "knowledge:java-backend-guide";

    private final CareerHubResumeClient resumeClient;
    private final CareerHubJobClient jobClient;
    private final ResumePdfTextExtractor pdfExtractor;
    private final RagIndexService indexService;
    private final RagQaService ragQaService;
    private final TextChunker chunker = new TextChunker();

    public CareerHubRagQaService(
            CareerHubResumeClient resumeClient,
            CareerHubJobClient jobClient,
            ResumePdfTextExtractor pdfExtractor,
            RagIndexService indexService,
            RagQaService ragQaService
    ) {
        this.resumeClient = resumeClient;
        this.jobClient = jobClient;
        this.pdfExtractor = pdfExtractor;
        this.indexService = indexService;
        this.ragQaService = ragQaService;
    }

    public String answer(
            Long resumeId,
            Long jobId,
            String authorization,
            String question,
            int k,
            double maxDistance
    ) {
        if (resumeId == null || resumeId <= 0
                || jobId == null || jobId <= 0) {
            throw new IllegalArgumentException("简历 ID 和岗位 ID 必须大于 0");
        }
        if (authorization == null || authorization.isBlank()) {
            throw new IllegalArgumentException("登录凭证不能为空");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        if (k <= 0 || !Double.isFinite(maxDistance) || maxDistance < 0) {
            throw new IllegalArgumentException("检索参数不合法");
        }

        // Bearer 凭证仅用于向 CareerHub 下载选定简历，不进入向量索引或模型提示词。
        byte[] pdfBytes =
                resumeClient.downloadResumePdf(resumeId, authorization);
        String resumeText = pdfExtractor.extractText(pdfBytes);

        JobDTO job = jobClient.getJobById(jobId);
        if (job == null || job.description() == null
                || job.description().isBlank()) {
            throw new IllegalArgumentException("未找到有效的岗位 JD");
        }

        // 不同来源使用不同 documentKey，检索时才能分别标注简历与岗位证据。
        String resumeKey = "resume:" + resumeId;
        String jobKey = "job:" + jobId;

        indexService.rebuildDocument(
                resumeKey,
                chunker.splitByParagraph(
                        "resume-" + resumeId + ".pdf", resumeText, 600
                )
        );
        indexService.rebuildDocument(
                jobKey,
                chunker.splitByParagraph(
                        "job-" + jobId + ".txt", job.description(), 600
                )
        );

        rebuildKnowledgeBase();

        return ragQaService.compareResumeAndJob(
                resumeKey, jobKey, KNOWLEDGE_DOCUMENT_KEY,
                question, k, maxDistance
        );
    }

    private void rebuildKnowledgeBase() {
        ClassPathResource resource = new ClassPathResource(
                "knowledge/java-backend-guide.txt"
        );

        try (InputStream input = resource.getInputStream()) {
            String text = new String(
                    input.readAllBytes(), StandardCharsets.UTF_8
            );

            indexService.rebuildDocument(
                    KNOWLEDGE_DOCUMENT_KEY,
                    chunker.splitByParagraph(
                            "java-backend-guide.txt", text, 600
                    )
            );
        } catch (IOException e) {
            throw new IllegalStateException("读取知识库文件失败", e);
        }
    }
}
