package com.zzztie.careerai.service;

import com.zzztie.careerai.dto.ResumeParseResult;
import com.zzztie.careerai.exception.ResumeParseException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import com.zzztie.careerai.client.CareerHubResumeClient;

@Service
public class ResumeParseService {

    private final ChatClient chatClient;
    private final CareerHubResumeClient careerHubResumeClient;
    private final ResumePdfTextExtractor resumePdfTextExtractor;

    public ResumeParseService(
            ChatClient.Builder builder,
            CareerHubResumeClient careerHubResumeClient,
            ResumePdfTextExtractor resumePdfTextExtractor
    ) {
        this.chatClient = builder.build();
        this.careerHubResumeClient = careerHubResumeClient;
        this.resumePdfTextExtractor = resumePdfTextExtractor;
    }

    public ResumeParseResult parseByResumeId(
            Long resumeId,
            String authorization
    ) {
        // 1. 从 CareerHub 下载简历 PDF
        byte[] pdfBytes = careerHubResumeClient.downloadResumePdf(
                resumeId,
                authorization
        );
        // 2. 从 PDF 中提取文字
        String resumeText = resumePdfTextExtractor.extractText(pdfBytes);
        // 3. 复用已有的 AI 解析方法
        return parse(resumeText);
    }

    public ResumeParseResult parse(String resumeText) {
        try {
            // 1. 调用大模型并尝试转换为 Java 对象
            ResumeParseResult result = chatClient.prompt()
                    .system("""
                            你是一名简历信息提取助手。
                            只提取简历中明确存在的信息。
                            请提取：
                            skills：技能
                            education：教育经历
                            projects：项目经历
                            不得编造信息。
                            没有相关信息时返回空数组。
                            """)
                    .user(u -> u.text("""
                            请解析以下简历：
                            {resume}
                            """)
                            .param("resume", resumeText))
                    .call()
                    .entity(ResumeParseResult.class);
            // 2. 校验模型输出是否满足业务要求
            if (result == null
                    || result.skills() == null
                    || result.education() == null
                    || result.projects() == null) {

                throw new IllegalStateException(
                        "AI返回的数据缺少必要字段"
                );
            }
            // 3. 校验成功，返回结构化结果
            return result;
        } catch (RuntimeException e) {
            // 4. 将调用、转换和校验异常统一转换为业务异常
            throw new ResumeParseException(e);
        }
    }
}