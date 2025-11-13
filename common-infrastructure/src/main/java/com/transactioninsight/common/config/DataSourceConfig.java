package com.transactioninsight.common.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 类说明 / Class Description:
 * 中文：集中式数据源配置，统一读取 spring.datasource.* 并构建 HikariCP 连接池。
 * English: Centralized DataSource configuration, reading spring.datasource.* to build a HikariCP pool.
 *
 * 使用场景 / Use Cases:
 * 中文：为各模块提供一致的数据源初始化与池参数配置，支持事务实验与集成测试。
 * English: Provide consistent DataSource initialization and pool params for modules supporting transaction experiments and ITs.
 *
 * 设计目的 / Design Purpose:
 * 中文：降低重复配置，保障池参数在不同模块间一致，减少运行时隐患。
 * English: Reduce duplicated configuration and ensure consistent pool params across modules to minimize runtime risks.
 *
 * 涉及的核心组件说明 / Core Components:
 * 中文：DataSourceProperties、HikariConfig/HikariDataSource、条件化装配。
 * English: DataSourceProperties, HikariConfig/HikariDataSource, conditional wiring.
 */
@Configuration
public class DataSourceConfig {

    /**
     * 方法说明 / Method Description:
     * 中文：绑定 spring.datasource.* 到 DataSourceProperties，承载 JDBC 基本信息。
     * English: Bind spring.datasource.* to DataSourceProperties, carrying core JDBC info.
     *
     * 参数 / Parameters:
     * 无
     *
     * 返回值 / Return:
     * 中文说明：包含 URL、用户名、密码、驱动名的属性对象
     * English description: Property object containing URL, username, password, driver name
     *
     * 异常 / Exceptions:
     * 中文/英文：无（属性绑定失败时由 Spring 在启动期校验报错）
     */
    @Bean
    @ConfigurationProperties("spring.datasource")
    @Primary
    public DataSourceProperties dataSourceProperties() {
        // 中文：创建并返回 DataSourceProperties，供后续 Hikari 配置引用
        // English: Create and return DataSourceProperties for later Hikari configuration
        return new DataSourceProperties();
    }

    /**
     * 方法说明 / Method Description:
     * 中文：绑定 spring.datasource.hikari.* 到 HikariConfig，用于配置连接池参数。
     * English: Bind spring.datasource.hikari.* to HikariConfig for connection pool parameters.
     *
     * 参数 / Parameters:
     * 无
     *
     * 返回值 / Return:
     * 中文说明：Hikari 连接池配置对象
     * English description: Hikari connection pool configuration object
     *
     * 异常 / Exceptions:
     * 中文/英文：无
     */
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariConfig hikariConfig() {
        // 中文：创建 HikariConfig，参数由外部配置文件注入
        // English: Create HikariConfig with parameters injected from external configs
        return new HikariConfig();
    }

    /**
     * 方法说明 / Method Description:
     * 中文：根据属性动态构建 HikariDataSource，并启用连接池。
     * English: Dynamically build HikariDataSource from properties and enable the connection pool.
     *
     * 参数 / Parameters:
     * @param properties 中文说明：数据源基本属性（URL、用户名、密码、驱动）
     *                   English description: Base DataSource properties (URL, username, password, driver)
     * @param hikari     中文说明：连接池参数（最大/最小连接、超时等）
     *                   English description: Pool parameters (max/min connections, timeouts)
     *
     * 返回值 / Return:
     * 中文说明：已配置完成并可用的 DataSource
     * English description: A fully configured, ready-to-use DataSource
     *
     * 异常 / Exceptions:
     * 中文/英文：当 URL/凭据为空或驱动不存在时，可能抛出运行时异常
     */
    @Bean
    @ConditionalOnMissingBean
    @Primary
    @ConditionalOnProperty(prefix = "spring.datasource", name = "url")
    public DataSource dataSource(DataSourceProperties properties, HikariConfig hikari) {
        // 中文：应用 JDBC 基本参数到连接池配置
        // English: Apply base JDBC parameters to the pool configuration
        hikari.setJdbcUrl(properties.getUrl());
        hikari.setUsername(properties.getUsername());
        hikari.setPassword(properties.getPassword());
        hikari.setDriverClassName(properties.getDriverClassName());

        // 中文：返回连接池数据源实例
        // English: Return pooled DataSource instance
        return new HikariDataSource(hikari);
    }
}
