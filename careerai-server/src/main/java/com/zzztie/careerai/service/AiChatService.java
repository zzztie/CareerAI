package com.zzztie.careerai.service;

import com.zzztie.careerai.dto.ChatHistoryMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import org.springframework.ai.chat.messages.Message;


import java.util.ArrayList;
import java.util.List;


@Service
public class AiChatService {
    private final ChatClient chatClient;
    private final MessageService messageService;

    public AiChatService(
            ChatClient.Builder builder,
            MessageService messageService) {

        this.chatClient = builder.build();
        this.messageService = messageService;
    }
    public String chat(String message){
        return chatClient.prompt()
                .system("""
                        你是CareerAi 求职助手。
                        主要负责计算机相关的实习与求职咨询。
                        请用中文回答。
                        不要编造用户没有提供的个人经历或技能。
                        如果信息不足，应明确说明。
                        """)
                .user(message)
                .call()
                .content();
    }
    public Flux<String> chatStream(String message){
        return chatClient.prompt()
                .system("""
                        你是CareerHubAI 求职助手。
                        请用中文回答。
                        不要编造用户不存在的经历。
                        """)
                .user(message)
                .stream()
                .content();
    }
    public Flux<String> chatStreamWithMemory(
            long conversationId,
            String message) {
        List<ChatHistoryMessage> history =
                messageService.getHistory(conversationId);

        return Flux.defer(() -> {
            List<Message> messages = new ArrayList<>();
            for (ChatHistoryMessage item : history) {
                if ("user".equals(item.role())) {
                    messages.add(
                            new UserMessage(item.content())
                    );
                } else if ("assistant".equals(item.role())) {
                    messages.add(
                            new AssistantMessage(item.content())
                    );
                }
            }
            messageService.saveMessage(
                    conversationId,
                    "user",
                    message
            );
            StringBuilder answer = new StringBuilder();
            return chatClient.prompt()
                    .system("你是 CareerAI 求职助手，请使用中文回答，不要编造用户不存在的经历。")
                    .messages(messages)
                    .user(message)
                    .stream()
                    .content()
                    // 每收到一个片段，就追加到缓冲区
                    .doOnNext(chunk -> {
                        answer.append(chunk);
                    })
                    .doOnComplete(() -> {
                        if (answer.length() > 0) {
                            messageService.saveMessage(
                                    conversationId,
                                    "assistant",
                                    answer.toString()
                            );
                        }
                    });
        });
    }
}
