package com.zzztie.careerai.repository;

import com.zzztie.careerai.dto.ResumeAnalysisResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import java.util.Objects;

@Repository
public class ResumeAnalysisRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ResumeAnalysisRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }
    public void save(
            String resumeText,
            ResumeAnalysisResult result
    ){
        try {
            String strengthsJson =
                    objectMapper.writeValueAsString(
                            result.strengths()
                    );
            String improvementAreasJson =
                    objectMapper.writeValueAsString(
                            result.improvementAreas()
                    );
            String recommendationsJson =
                    objectMapper.writeValueAsString(
                            result.recommendations()
                    );
            String evidenceJson =
                    objectMapper.writeValueAsString(
                            result.evidence()
                    );
            jdbcTemplate.update("""
        INSERT INTO resume_analysis (
            resume_text,
            summary,
            strengths,
            improvement_areas,
            recommendations,
            evidence,
            safety_notice
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
                    resumeText,
                    result.summary(),
                    strengthsJson,
                    improvementAreasJson,
                    recommendationsJson,
                    evidenceJson,
                    result.safetyNotice()
            );
        }catch (JacksonException  e){
            throw new IllegalStateException(
                    "简历分析结果转化为Json失败",e);
        }
    }
}
