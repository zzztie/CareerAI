package com.zzztie.careerai.controller;

import com.zzztie.careerai.dto.SkillGapReport;
import com.zzztie.careerai.service.SkillGapAgentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/ai/agent")
public class SkillGapAgentController {

    private final SkillGapAgentService service;

    public SkillGapAgentController(SkillGapAgentService service) {
        this.service = service;
    }

    @PostMapping("/skill-gap")
    public SkillGapReport analyze(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Request request
    ) {
        if (request == null || request.resumeId() == null || request.resumeId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效的简历 ID");
        }
        if (request.goal() == null || request.goal().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "求职目标不能为空");
        }
        if (request.goal().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "求职目标不能超过 500 个字符");
        }
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.substring(7).isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "需要 CareerHub 登录凭证");
        }
        return service.analyze(request.resumeId(), authorization, request.goal().strip());
    }

    public record Request(Long resumeId, String goal) {
    }
}
