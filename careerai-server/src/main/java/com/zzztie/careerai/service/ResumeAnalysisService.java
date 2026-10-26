package com.zzztie.careerai.service;

import com.zzztie.careerai.dto.ResumeAnalysisResult;
import com.zzztie.careerai.dto.ResumeParseResult;
import com.zzztie.careerai.repository.ResumeAnalysisRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ResumeAnalysisService {
    private final ChatClient chatClient;
    private final ResumeParseService resumeParseService;
    private final ResumeAnalysisRepository resumeAnalysisRepository;
    public ResumeAnalysisService(
            ChatClient.Builder builder,
            ResumeParseService resumeParseService,
            ResumeAnalysisRepository resumeAnalysisRepository
    ){
        this.chatClient = builder.build();
        this.resumeParseService = resumeParseService;
        this.resumeAnalysisRepository = resumeAnalysisRepository;
    }
    // 1. 复用 U38：先提取简历中的结构化信息
    public ResumeAnalysisResult analyze(String resumeText){
        ResumeParseResult parsed =
                resumeParseService.parse(resumeText);
        // 2. 调用 AI，生成结构化分析结果
        ResumeAnalysisResult result = chatClient.prompt()
                .system("""
                        你是一名严谨的AI简历分析助手。
                        你必须遵守以下规则：
                        1.只根据简历原文和解析结果进行分析。
                        2. 不得编造用户的技能、学历、项目经历或成果。
                        3. strengths 只能记录有简历依据的优势。
                        4. improvementAreas 应指出可以改进的具体方面，
                           不得把未提及的信息直接判定为用户不会。
                        5. recommendations 必须是具体、可执行的建议。
                        6. evidence 必须列出支持分析结论的简历事实。
                        7. 不得要求用户伪造项目经历或成果数据。
                        8. 如果信息不足，应明确说明证据不足。

                        请返回符合 ResumeAnalysisResult 结构的数据，
                        包含 summary、strengths、improvementAreas、
                        recommendations、evidence 和 safetyNotice。
                        strengths、improvementAreas、
                        recommendations、evidence 均为字符串数组。
                        """)
                .user( u -> u.text("""
                        请分析以下简历。
                        【简历原文】
                        {resume}

                        【简历结构化解析结果】
                        {parsed}
                        """)
                        .param("resume",resumeText)
                        .param("parsed",parsed.toString()))
                .call()
                .entity(ResumeAnalysisResult.class);
        // 3. 检查模型是否返回了必要字段
        if(result == null
                || result.summary() == null
                || result.strengths() == null
                || result.improvementAreas() == null
                || result.recommendations() == null
                || result.evidence() == null){
            throw new IllegalStateException("AI返回的简历分析结果缺少必要字段");
        }
        ResumeAnalysisResult finalResult = new ResumeAnalysisResult(
                result.summary(),
                result.strengths(),
                result.improvementAreas(),
                result.recommendations(),
                result.evidence(),
                "本分析仅供求职准备参考。请核实所有事实，"
                + "不得将AI建议当做真实经历或成果。"
        );
        resumeAnalysisRepository.save(resumeText,finalResult);
        return finalResult;
    }
}
