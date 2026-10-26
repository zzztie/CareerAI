package com.zzztie.careerai.dto;

import java.util.List;

public record ResumeAnalysisResult (
    String summary,
    List<String> strengths,
    List<String> improvementAreas,
    List<String> recommendations,
    List<String> evidence,
    String safetyNotice
){

}
