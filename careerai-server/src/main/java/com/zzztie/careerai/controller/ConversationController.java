package com.zzztie.careerai.controller;

import com.zzztie.careerai.service.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai/conversations")
public class ConversationController {
    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Long> create(
            @RequestBody Map<String,String> request
    ){
        String title = request.get("title");
        long id = conversationService.createConversation(title);
        return Map.of("conversationId",id);
    }
}
