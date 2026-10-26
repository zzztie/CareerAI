package com.zzztie.careerai.controller;

import com.zzztie.careerai.service.AiChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.io.Flushable;
import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiChatController {
    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }
    @PostMapping("/chat")
    public Map<String,String> chat(
            @RequestBody Map<String,String> request
    ){
        String message = request.get("message");
        if(message == null || message.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "message 不能为空"
            );
        }
        String answer = aiChatService.chat(message);
        return Map.of("answer", answer);
    }
    @PostMapping(
            value = "/chat/stream",
            produces = "text/event-stream;charset=UTF-8"
    )
    public Flux<String> chatStream(
            @RequestBody Map<String,String> request
    ){
        String message = request.get("message");
        if(message == null || message.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "message 不能为空"
            );
        }
        return aiChatService.chatStream(message);
    }
    @PostMapping(
            value = "/conversations/{conversationId}/chat/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<String> chatStreamWithMemory(
            @PathVariable long conversationId,
            @RequestBody Map<String,String> request
    ){
        String message = request.get("message");
        if (conversationId<=0){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "会话ID不合法"
            );
        }
        if (message == null || message.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "消息不能为空"
            );
        }
        return aiChatService.chatStreamWithMemory(conversationId,message);
    }
}
