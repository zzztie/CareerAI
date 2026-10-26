package com.zzztie.careerai.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RagDataSourceConfig {

    @Bean(name = "ragDataSource", defaultCandidate = false)
    @ConfigurationProperties("rag.datasource")
    public HikariDataSource ragDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }
}