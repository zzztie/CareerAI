package com.zzztie.careerai.controller;

import com.zzztie.careerai.dto.ChatHistoryMessage;
import com.zzztie.careerai.service.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai/conversations")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public void saveMessage(
            @PathVariable long conversationId,
            @RequestBody Map<String, String> request) {
        String role = request.get("role");
        String content = request.get("content");

        messageService.saveMessage(
                conversationId,
                role,
                content
        );
    }

    @GetMapping("/{conversationId}/messages")
    public List<ChatHistoryMessage> getHistory(
            @PathVariable long conversationId) {
        return messageService.getHistory(conversationId);
    }
}