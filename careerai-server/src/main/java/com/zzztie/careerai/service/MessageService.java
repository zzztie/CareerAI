package com.zzztie.careerai.service;

import com.zzztie.careerai.dto.ChatHistoryMessage;
import com.zzztie.careerai.repository.ConversationRepository;
import com.zzztie.careerai.repository.MessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository) {

        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
    }

    private void checkConversation(long conversationId) {
        if (conversationId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "会话ID不合法"
            );
        }
        if (!conversationRepository.existsById(conversationId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "会话不存在"
            );
        }
    }

    public void saveMessage(long conversation_id,
                            String role,
                            String content){
        checkConversation(conversation_id);
        if(!"user".equals(role) && !"assistant".equals(role)){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "消息角色不合法"
            );
        }
        if(content == null || content.isBlank()){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "消息内容不为空"
            );
        }
        messageRepository.save(conversation_id,role,content);
    }

    public List<ChatHistoryMessage> getHistory(long conversationId){
        checkConversation(conversationId);
        return messageRepository.findByConversationId(conversationId);
    }

}
