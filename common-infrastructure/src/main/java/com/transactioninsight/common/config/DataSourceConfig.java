package com.transactioninsight.common.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * <p>
 * Centralised datasource configuration shared by every module in the multi-module Maven build.
 * The configuration intentionally keeps the settings environment agnostic so the same
 * {@link javax.sql.DataSource} can be reused by command-line demos, integration tests and IDE
 * driven experiments.
 * </p>
 *
 * <p>
 * 核心思路：通过 {@link DataSourceProperties} 读取 YAML/Properties 中的连接信息，再构建出带有
 * 面试场景常见调优参数的 {@link HikariDataSource}。这样既能保持配置一致性，也便于后续在不同模块中复用。
 * </p>
 */
@Configuration
public class DataSourceConfig {

    /**
     * 读取 spring.datasource.* 配置并封装成 {@link DataSourceProperties}。
     *
     * @return 提供 JDBC 连接元信息的配置对象
     */
    @Bean
    @ConfigurationProperties("spring.datasource")
    @Primary
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * 创建一个带有常用池化参数的 {@link DataSource}，供业务代码和测试复用。
     *
     * @param properties 上面方法构建的属性对象，包含 JDBC URL、账号等
     * @return 线程安全、支持连接池的 {@link HikariDataSource}
     */
    @Bean
    @ConditionalOnMissingBean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        // 核心配置：显式设置池大小与超时时间，确保在压力测试或并发实验中保持稳定。
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(properties.getDriverClassName());
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(60_000);
        config.setIdleTimeout(300_000);
        config.setPoolName("transaction-insight-pool");
        return new HikariDataSource(config);
    }
}
