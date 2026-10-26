package com.zzztie.careerai.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;

@Repository
public class ConversationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long create(String title) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO conversation (title) VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, title);
            return ps;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("创建会话失败：未获取到主键");
        }
        return id.longValue();
    }
    public boolean existsById(long conversationId) {

        Integer result = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM conversation WHERE id = ?)",
                Integer.class,
                conversationId
        );

        return result != null && result == 1;
    }
}