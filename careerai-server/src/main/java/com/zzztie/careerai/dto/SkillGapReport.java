package com.zzztie.careerai.dto;

import java.util.List;

/**
 * 岗位技能缺口分析结果。状态由 Java 根据简历、实际取得的完整 JD、
 * 有效分析数量和内容截断情况计算，不能直接采用模型的完成声明。
 */
public record SkillGapReport(
        Status status,
        Long resumeId,
        int stepsUsed,
        List<JobResult> jobs,
        List<String> limitations,
        String traceId
) {
    public enum Status {
        COMPLETE, PARTIAL, FAILED
    }

    public record JobResult(
            Long jobId,
            String title,
            String company,
            List<String> matchedEvidence,
            List<String> missingEvidence,
            List<String> recommendations
    ) {
    }
}
