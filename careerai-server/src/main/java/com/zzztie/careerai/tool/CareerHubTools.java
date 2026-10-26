package com.zzztie.careerai.tool;

import com.zzztie.careerai.client.CareerHubJobClient;
import com.zzztie.careerai.client.CareerHubResumeClient;
import com.zzztie.careerai.dto.JobDTO;
import com.zzztie.careerai.dto.JobSearchPage;
import com.zzztie.careerai.service.ResumePdfTextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 每个请求新建一个实例：可读取的简历 ID 和凭证由服务器绑定，
 * 模型只能选择工具和填写工具参数。调用计数也保存在这个实例中，
 * 因此同一次模型对话的多轮工具调用共享相同限额。
 */
public class CareerHubTools {

    private static final Logger log = LoggerFactory.getLogger(CareerHubTools.class);
    private final Long allowedResumeId;
    private final String authorization;
    private final CareerHubResumeClient resumeClient;
    private final ResumePdfTextExtractor pdfExtractor;
    private final CareerHubJobClient jobClient;
    private final AtomicInteger resumeCalls = new AtomicInteger();
    private final AtomicInteger jobCalls = new AtomicInteger();
    private final AtomicInteger searchCalls = new AtomicInteger();

    public CareerHubTools(
            Long allowedResumeId,
            String authorization,
            CareerHubResumeClient resumeClient,
            ResumePdfTextExtractor pdfExtractor,
            CareerHubJobClient jobClient
    ) {
        this.allowedResumeId = allowedResumeId;
        this.authorization = authorization;
        this.resumeClient = resumeClient;
        this.pdfExtractor = pdfExtractor;
        this.jobClient = jobClient;
    }

    @Tool(description = "读取本次请求选定的简历文本。只在需要核对候选人经历时调用。")
    public String getResume(
            @ToolParam(description = "本次请求选定的简历 ID") Long resumeId
    ) {
        log.info("event=tool.resume_read_requested resumeId={}", resumeId);
        // 模型给出的 ID 不可信；只能读取入口请求已经选定的那份简历。
        if (allowedResumeId == null) {
            return "工具失败：本次请求未选择简历。";
        }
        if (!allowedResumeId.equals(resumeId)) {
            return "工具失败：无权读取该简历；只能读取本次请求选定的简历。";
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "工具失败：读取简历需要 CareerHub 登录凭证。";
        }
        if (resumeCalls.incrementAndGet() > 1) {
            return "工具失败：本次请求读取简历的次数已达上限。";
        }
        try {
            byte[] pdf = resumeClient.downloadResumePdf(resumeId, authorization);
            String resumeText = pdfExtractor.extractText(pdf);
            return "简历 ID " + resumeId + "，内容：\n" + limit(resumeText, 5000);
        } catch (ResponseStatusException e) {
            log.warn("event=tool.resume_read_failed resumeId={} httpStatus={}",
                    resumeId, e.getStatusCode().value());
            return switch (e.getStatusCode().value()) {
                case 401 -> "工具失败：CareerHub 登录凭证无效或已过期。";
                case 403 -> "工具失败：当前用户无权读取这份简历。";
                case 404 -> "工具失败：选定的简历不存在。";
                default -> "工具失败：暂时无法从 CareerHub 读取简历。";
            };
        } catch (IllegalArgumentException e) {
            log.warn("event=tool.resume_extract_failed resumeId={}", resumeId);
            return "工具失败：简历 PDF 无法提取文字。";
        } catch (RuntimeException e) {
            log.error("event=tool.resume_read_failed resumeId={} exceptionType={}",
                    resumeId, e.getClass().getSimpleName());
            return "工具失败：读取简历时发生异常。";
        }
    }

    @Tool(description = "根据岗位 ID 读取岗位标题、公司和完整 JD。")
    public String getJob(
            @ToolParam(description = "岗位 ID") Long jobId
    ) {
        log.info("event=tool.job_read_requested jobId={}", jobId);
        if (jobId == null || jobId <= 0) {
            return "工具失败：岗位 ID 必须大于 0。";
        }
        if (jobCalls.incrementAndGet() > 3) {
            return "工具失败：本次请求读取岗位详情的次数已达上限。";
        }
        JobDTO job;
        try {
            job = jobClient.getJobById(jobId);
        } catch (RestClientResponseException e) {
            log.warn("event=tool.job_read_failed jobId={} httpStatus={}",
                    jobId, e.getStatusCode().value());
            return e.getStatusCode().value() == 404
                    ? "工具失败：岗位不存在或已下线。"
                    : "工具失败：暂时无法读取岗位详情。";
        } catch (RestClientException e) {
            log.warn("event=tool.job_read_unavailable jobId={}", jobId);
            return "工具失败：岗位服务暂时不可用。";
        } catch (RuntimeException e) {
            log.error("event=tool.job_read_failed jobId={} exceptionType={}",
                    jobId, e.getClass().getSimpleName());
            return "工具失败：读取岗位详情时发生异常。";
        }
        if (job == null) {
            return "未找到岗位 " + jobId + "。";
        }
        return "岗位 ID " + job.id() + "；标题：" + job.title()
                + "；公司：" + job.company() + "；状态：" + job.status()
                + "；JD：\n" + limit(job.description(), 5000);
    }

    @Tool(description = "按关键词搜索在招岗位，最多返回 5 条岗位概要。")
    public String searchJobs(
            @ToolParam(description = "岗位关键词，例如 Java") String keyword
    ) {
        String safeKeyword = keyword == null ? ""
                : keyword.strip().replaceAll("[\\r\\n\\t]", " ");
        // 搜索词来自用户或模型，只记录长度，不把原文写入日志。
        log.info("event=tool.job_search_requested keywordLength={}", safeKeyword.length());
        if (safeKeyword.isBlank()) {
            return "工具失败：搜索关键词不能为空。";
        }
        if (safeKeyword.length() > 80) {
            return "工具失败：搜索关键词不能超过 80 个字符。";
        }
        if (searchCalls.incrementAndGet() > 3) {
            return "工具失败：本次请求搜索岗位的次数已达上限。";
        }
        JobSearchPage page;
        try {
            page = jobClient.searchOpenJobs(safeKeyword);
        } catch (RestClientResponseException e) {
            log.warn("event=tool.job_search_failed httpStatus={}", e.getStatusCode().value());
            return "工具失败：岗位搜索暂时不可用。";
        } catch (RestClientException e) {
            log.warn("event=tool.job_search_unavailable");
            return "工具失败：岗位服务暂时不可用。";
        } catch (RuntimeException e) {
            log.error("event=tool.job_search_failed exceptionType={}",
                    e.getClass().getSimpleName());
            return "工具失败：搜索岗位时发生异常。";
        }
        List<JobDTO> jobs = page == null ? null : page.records();
        if (jobs == null || jobs.isEmpty()) {
            return "没有找到匹配的在招岗位。";
        }
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (JobDTO job : jobs) {
            if (job == null) {
                continue;
            }
            if (count++ == 5) {
                break;
            }
            result.append("岗位 ID ").append(job.id())
                    .append("；标题：").append(job.title())
                    .append("；公司：").append(job.company())
                    .append("；简介：").append(limit(job.summary(), 200))
                    .append('\n');
        }
        return result.length() == 0 ? "没有找到匹配的在招岗位。"
                : result.toString();
    }

    private static String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength
                ? value : value.substring(0, maxLength) + "…（内容已截断）";
    }
}
