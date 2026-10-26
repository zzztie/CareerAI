package com.zzztie.careerai.service;

import com.zzztie.careerai.dto.DocumentChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.main.lazy-initialization=true",
        "spring.datasource.username=test-only",
        "spring.datasource.password=test-only"
})
class RagQaServiceIntegrationTest {

    @Autowired
    private RagIndexService indexService;

    @Autowired
    private RagQaService ragQaService;

    @Autowired
    @Qualifier("ragDataSource")
    private DataSource ragDataSource;

    @Test
    void answersWithRealResumeAndJobCitations() {
        String resumeKey = "u45-resume-" + UUID.randomUUID();
        String jobKey = "u45-job-" + UUID.randomUUID();
        JdbcTemplate jdbc = new JdbcTemplate(ragDataSource);

        try {
            indexService.rebuildDocument(resumeKey, List.of(
                    new DocumentChunk(
                            "resume.txt", 1,
                            "有 Java 和 Spring Boot 后端接口开发经验。"
                    )
            ));
            indexService.rebuildDocument(jobKey, List.of(
                    new DocumentChunk(
                            "job.txt", 1,
                            "招聘 Java 后端实习生，要求熟悉 Java 和 Spring Boot。"
                    )
            ));

            String result = ragQaService.compareResumeAndJob(
                    resumeKey,
                    jobKey,
                    "简历是否符合这个岗位的 Java 和 Spring Boot 要求？",
                    2,
                    2.0
            );

            System.out.println(result);

            int sourcesStart = result.indexOf("\n\n检索到的来源：");
            assertTrue(sourcesStart >= 0, "结果中应有来源清单");

            String answerOnly = result.substring(0, sourcesStart);
            assertTrue(answerOnly.contains("[R1]"), "回答正文应引用简历");
            assertTrue(answerOnly.contains("[J1]"), "回答正文应引用岗位 JD");
            assertTrue(result.contains("[R1] 简历：resume.txt，第 1 块"));
            assertTrue(result.contains("[J1] 岗位 JD：job.txt，第 1 块"));
        } finally {
            jdbc.update("DELETE FROM rag_chunk WHERE document_key = ?", resumeKey);
            jdbc.update("DELETE FROM rag_chunk WHERE document_key = ?", jobKey);
        }
    }
}