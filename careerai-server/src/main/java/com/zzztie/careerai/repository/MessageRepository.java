package com.zzztie.careerai.repository;

import com.zzztie.careerai.dto.ChatHistoryMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.zzztie.careerai.dto.ChatHistoryMessage;
import java.util.List;
import java.sql.PreparedStatement;
import java.util.List;

@Repository
public class MessageRepository {
    private final JdbcTemplate jdbcTemplate;

    public MessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    public void save(long conversationId,
                     String role,
                     String content){
        jdbcTemplate.update("INSERT INTO message (conversation_id,role,content) values (?,?,?)",
                conversationId,role,content);
    }

    public List<ChatHistoryMessage> findByConversationId(long conversationId){
        String sql = """
                SELECT role,content FROM message
                WHERE conversation_id = ?
                ORDER BY id ASC
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ChatHistoryMessage(
                        rs.getString("role"),
                        rs.getString("content")
                ),
                conversationId
        );
    }
}
