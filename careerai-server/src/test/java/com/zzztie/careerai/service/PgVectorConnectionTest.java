package com.zzztie.careerai.service;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

@SpringBootTest(properties = {
        "spring.main.lazy-initialization=true",
        "spring.ai.deepseek.api-key=test-only",
        "spring.datasource.username=test-only",
        "spring.datasource.password=test-only"
})
class PgVectorConnectionTest {

    @Autowired
    @Qualifier("ragDataSource")
    private DataSource ragDataSource;

    @Autowired
    private JdbcTemplate mysqlJdbcTemplate;

    @Test
    void springKeepsBothConnectionsSeparate() {
        assertNotSame(ragDataSource, mysqlJdbcTemplate.getDataSource());

        Long count = new JdbcTemplate(ragDataSource)
                .queryForObject("SELECT count(*) FROM rag_chunk", Long.class);

        assertNotNull(count);
        System.out.println("PostgreSQL rag_chunk 行数：" + count);
    }
}