package com.xhr.springai.officeSurvivalGuide.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    // ================== 专家库 (PostgreSQL) ==================
    @Primary
    @Bean(name = "pgDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.pg")
    public DataSource pgDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Primary
    @Bean(name = "pgJdbcTemplate")
    public JdbcTemplate pgJdbcTemplate(@Qualifier("pgDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    // ================== 验证库 (TiDB) ==================
    @Bean(name = "tidbDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.tidb")
    public DataSource tidbDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "tidbJdbcTemplate")
    public JdbcTemplate tidbJdbcTemplate(@Qualifier("tidbDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
