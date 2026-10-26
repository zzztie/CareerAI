package com.zzztie.careerai.controller;

import com.zzztie.careerai.dto.ResumeParseResult;
import com.zzztie.careerai.service.ResumeParseService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@RestController
@RequestMapping("/ai/resume")
public class ResumeParseController {
    private final ResumeParseService resumeParseService;

    public ResumeParseController(ResumeParseService resumeParseService) {
        this.resumeParseService = resumeParseService;
    }
    @PostMapping("/parse")
    public ResumeParseResult parse(
            @RequestBody Map<String,String> request
    ){
        String resumeText = request.get("resumeText");
        if(resumeText == null || resumeText.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "简历内容不能为空"
            );
        }
        return resumeParseService.parse(resumeText);
    }

    @PostMapping("/parse/{resumeId}")
    public ResumeParseResult parseByResumeId(
            @PathVariable("resumeId") Long resumeId,
            @RequestHeader("Authorization") String authorization
    ) {
        if (resumeId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "简历 ID 必须大于 0"
            );
        }
        if (authorization.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "登录凭证不能为空"
            );
        }
        return resumeParseService.parseByResumeId(
                resumeId,
                authorization
        );
    }
}
