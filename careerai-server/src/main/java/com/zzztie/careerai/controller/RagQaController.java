package com.zzztie.careerai.controller;

import com.zzztie.careerai.service.CareerHubRagQaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/ai/rag")
public class RagQaController {

    private final CareerHubRagQaService ragService;

    public RagQaController(CareerHubRagQaService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/compare/{resumeId}/{jobId}")
    public Map<String, String> compare(
            @PathVariable Long resumeId,
            @PathVariable Long jobId,
            @RequestHeader(value = "Authorization", required = false)
            String authorization,
            @RequestBody Map<String, String> request
    ) {
        if (authorization == null || authorization.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "登录凭证不能为空"
            );
        }
        if (resumeId == null || resumeId <= 0
                || jobId == null || jobId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "简历 ID 和岗位 ID 必须大于 0"
            );
        }

        String question = request.get("question");
        if (question == null || question.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "问题不能为空"
            );
        }

        String answer = ragService.answer(
                resumeId, jobId, authorization, question,
                4, 0.6
        );
        return Map.of("answer", answer);
    }
}
