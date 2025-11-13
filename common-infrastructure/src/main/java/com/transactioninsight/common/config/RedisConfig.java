package com.transactioninsight.common.config;

import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 类说明 / Class Description:
 * 中文：Redis 基础设施配置，统一连接工厂与模板，适配不同模块的缓存/限流/锁示例。
 * English: Redis infrastructure configuration, unifying connection factory and template for cache/rate-limit/lock samples.
 *
 * 使用场景 / Use Cases:
 * 中文：在各模块中直接注入 Redis 相关 Bean，避免重复连接配置。
 * English: Inject Redis beans across modules to avoid duplicated connection configurations.
 *
 * 设计目的 / Design Purpose:
 * 中文：通过统一连接参数与超时设置，提升稳定性并简化接入。
 * English: Improve stability and simplify integration via unified connection parameters and timeouts.
 */
@Configuration
public class RedisConfig {

    /**
     * 方法说明 / Method Description:
     * 中文：构建 LettuceConnectionFactory，应用超时与密码等连接参数。
     * English: Build LettuceConnectionFactory applying timeouts and password connection parameters.
     *
     * 参数 / Parameters:
     * @param properties 中文说明：Spring 读取的 redis.* 属性（主机、端口、密码、超时）
     *                   English description: Spring-read redis.* properties (host, port, password, timeout)
     *
     * 返回值 / Return:
     * 中文说明：Redis 连接工厂
     * English description: Redis connection factory
     *
     * 异常 / Exceptions:
     * 中文/英文：主机不可达或认证失败可能导致运行时异常
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory(RedisProperties properties) {
        // 中文：推断命令超时（默认 2 秒），避免阻塞主线程
        // English: Infer command timeout (default 2s) to avoid blocking main thread
        Duration timeout = properties.getTimeout() == null ? Duration.ofSeconds(2) : properties.getTimeout();
        // 中文：构建客户端配置并设置命令超时
        // English: Build client configuration and set command timeout
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .build();

        // 中文：声明单机配置（主机、端口、密码）
        // English: Declare standalone configuration (host, port, password)
        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
        serverConfig.setHostName(properties.getHost());
        serverConfig.setPort(properties.getPort());
        if (properties.getPassword() != null) {
            serverConfig.setPassword(RedisPassword.of(properties.getPassword()));
        }
        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }

    /**
     * 方法说明 / Method Description:
     * 中文：创建字符串模板，便于直接操作 String 类型键值。
     * English: Create string template for straightforward String key/value operations.
     *
     * 参数 / Parameters:
     * @param factory 中文说明：上面创建的连接工厂
     *                English description: The connection factory created above
     *
     * 返回值 / Return:
     * 中文说明：StringRedisTemplate
     * English description: StringRedisTemplate
     *
     * 异常 / Exceptions:
     * 中文/英文：无（构造失败由 Spring 抛出运行时异常）
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        // 中文：构建并返回字符串模板
        // English: Build and return StringRedisTemplate
        return new StringRedisTemplate(factory);
    }
}
