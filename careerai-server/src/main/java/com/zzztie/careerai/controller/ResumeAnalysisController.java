package com.zzztie.careerai.controller;

import com.zzztie.careerai.dto.ResumeAnalysisResult;
import com.zzztie.careerai.service.ResumeAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/ai/resume")
public class ResumeAnalysisController {
    private final ResumeAnalysisService resumeAnalysisService;

    public ResumeAnalysisController(ResumeAnalysisService resumeAnalysisService) {
        this.resumeAnalysisService = resumeAnalysisService;
    }

    @PostMapping("/analyze")
    public ResumeAnalysisResult analyze(
            @RequestBody Map<String,String> request
            ){
        String resumeText = request.get("resumeText");
        if (resumeText == null || resumeText.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "简历内容不能为空"
            );
        }
        return resumeAnalysisService.analyze(resumeText);
    }
}
