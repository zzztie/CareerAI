package com.zzztie.careerai.controller;

import com.zzztie.careerai.service.ToolCallingDemoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/ai/tools")
public class ToolCallingDemoController {

    private final ToolCallingDemoService service;

    public ToolCallingDemoController(ToolCallingDemoService service) {
        this.service = service;
    }

    @PostMapping("/demo")
    public Map<String, String> demo(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @RequestBody DemoRequest request
    ) {
        if (request == null || request.question() == null
                || request.question().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "问题不能为空"
            );
        }
        Long resumeId = request.resumeId();
        if (resumeId != null && resumeId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "简历 ID 必须大于 0"
            );
        }
        if (resumeId != null
                && (authorization == null
                || !authorization.startsWith("Bearer "))) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "读取简历需要 CareerHub 登录凭证"
            );
        }
        return Map.of("answer", service.answer(
                resumeId, authorization, request.question()
        ));
    }

    @PostMapping("/chain")
    public Map<String, String> chain(
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @RequestBody DemoRequest request
    ) {
        if (request == null || request.question() == null
                || request.question().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "问题不能为空"
            );
        }
        if (request.resumeId() == null || request.resumeId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "请选择有效的简历 ID"
            );
        }
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.substring(7).isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "读取简历需要 CareerHub 登录凭证"
            );
        }
        return Map.of("answer", service.answerChain(
                request.resumeId(), authorization, request.question()
        ));
    }

    public record DemoRequest(String question, Long resumeId) {
    }
}
