package com.zzztie.careerai.tool;

import com.zzztie.careerai.client.CareerHubJobClient;
import com.zzztie.careerai.client.CareerHubResumeClient;
import com.zzztie.careerai.dto.JobDTO;
import com.zzztie.careerai.dto.JobSearchPage;
import com.zzztie.careerai.service.ResumePdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CareerHubToolsTest {

    @Test
    void exposesOnlyTheThreeReadToolsWithoutAnAuthorizationParameter() {
        CareerHubTools tools = new CareerHubTools(
                10L, "Bearer test-token",
                mock(CareerHubResumeClient.class),
                mock(ResumePdfTextExtractor.class),
                mock(CareerHubJobClient.class)
        );
        var callbacks = ToolCallbacks.from(tools);
        Set<String> names = Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertEquals(Set.of("getResume", "getJob", "searchJobs"), names);
        assertTrue(Arrays.stream(callbacks)
                .noneMatch(callback -> callback.getToolDefinition()
                        .inputSchema().contains("authorization")));
    }

    @Test
    void cannotReadAResumeOtherThanTheOneSelectedForThisRequest() {
        CareerHubResumeClient resumeClient = mock(CareerHubResumeClient.class);
        ResumePdfTextExtractor pdfExtractor = mock(ResumePdfTextExtractor.class);
        byte[] pdf = new byte[]{1, 2, 3};
        when(resumeClient.downloadResumePdf(10L, "Bearer test-token"))
                .thenReturn(pdf);
        when(pdfExtractor.extractText(pdf))
                .thenReturn("练习简历中的 Java 项目");
        CareerHubTools tools = new CareerHubTools(
                10L, "Bearer test-token", resumeClient, pdfExtractor,
                mock(CareerHubJobClient.class)
        );

        assertFalse(tools.getResume(11L).contains("Java 项目"));
        assertTrue(tools.getResume(11L).contains("无权读取"));
        verifyNoInteractions(resumeClient, pdfExtractor);

        assertTrue(tools.getResume(10L).contains("Java 项目"));
        verify(resumeClient).downloadResumePdf(10L, "Bearer test-token");
        verify(pdfExtractor).extractText(pdf);
    }

    @Test
    void downstreamFailuresBecomeSafeToolResults() {
        CareerHubResumeClient resumeClient = mock(CareerHubResumeClient.class);
        ResumePdfTextExtractor pdfExtractor = mock(ResumePdfTextExtractor.class);
        CareerHubJobClient jobClient = mock(CareerHubJobClient.class);
        CareerHubTools tools = new CareerHubTools(
                10L, "Bearer test-token", resumeClient, pdfExtractor, jobClient
        );
        when(resumeClient.downloadResumePdf(10L, "Bearer test-token"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        when(jobClient.getJobById(7L))
                .thenThrow(new ResourceAccessException("offline"));
        when(jobClient.searchOpenJobs("Java"))
                .thenThrow(new ResourceAccessException("offline"));

        assertTrue(tools.getResume(10L).contains("工具失败"));
        assertTrue(tools.getResume(11L).contains("无权读取"));
        assertTrue(tools.getJob(7L).contains("工具失败"));
        assertTrue(tools.searchJobs("Java").contains("工具失败"));
        verifyNoInteractions(pdfExtractor);
    }

    @Test
    void searchCallLimitStopsFurtherDownstreamRequests() {
        CareerHubJobClient jobClient = mock(CareerHubJobClient.class);
        when(jobClient.searchOpenJobs("Java"))
                .thenReturn(new JobSearchPage(List.of()));
        CareerHubTools tools = new CareerHubTools(
                null, null, mock(CareerHubResumeClient.class),
                mock(ResumePdfTextExtractor.class), jobClient
        );

        for (int i = 0; i < 3; i++) {
            assertTrue(tools.searchJobs("Java").contains("没有找到"));
        }
        assertTrue(tools.searchJobs("Java").contains("上限"));
        verify(jobClient, times(3)).searchOpenJobs("Java");
    }

    @Test
    void searchResultContainsAtMostFiveJobs() {
        CareerHubJobClient jobClient = mock(CareerHubJobClient.class);
        List<JobDTO> jobs = LongStream.rangeClosed(1, 6)
                .mapToObj(id -> new JobDTO(id, null, null, "岗位" + id,
                        "公司", "JD", "OPEN", "简介", "JOB"))
                .toList();
        when(jobClient.searchOpenJobs("Java"))
                .thenReturn(new JobSearchPage(jobs));
        CareerHubTools tools = new CareerHubTools(
                null, null, mock(CareerHubResumeClient.class),
                mock(ResumePdfTextExtractor.class), jobClient
        );

        String result = tools.searchJobs("Java");
        assertTrue(result.contains("岗位 ID 5"));
        assertFalse(result.contains("岗位 ID 6"));
    }
}
