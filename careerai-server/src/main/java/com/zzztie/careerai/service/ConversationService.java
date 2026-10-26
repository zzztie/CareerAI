package com.zzztie.careerai.service;

import com.zzztie.careerai.repository.ConversationRepository;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;

    public ConversationService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    public long createConversation(String title){
        if(title == null || title.isBlank()){
            title = "新会话";
        }
        if(title.length() >200){
            throw new IllegalArgumentException("会话标题过长");
        }
        return conversationRepository.create(title);
    }
}
