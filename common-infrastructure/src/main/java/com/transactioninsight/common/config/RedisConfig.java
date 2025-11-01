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
 * 提供 Redis 相关的基础设施 Bean，供各模块的缓存、限流或分布式锁样例复用。
 * 通过 {@link RedisProperties} 自动装配参数，避免在不同模块重复配置连接信息。
 */
@Configuration
public class RedisConfig {

    /**
     * 构建一个基于 Lettuce 的 {@link RedisConnectionFactory}。
     *
     * @param properties Spring Boot 读取到的 redis.* 配置
     * @return 复用 Lettuce 客户端能力的连接工厂
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory(RedisProperties properties) {
        // 核心步骤：根据配置动态设置命令超时，避免演示长事务时阻塞主线程。
        Duration timeout = properties.getTimeout() == null ? Duration.ofSeconds(2) : properties.getTimeout();
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .build();

        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
        serverConfig.setHostName(properties.getHost());
        serverConfig.setPort(properties.getPort());
        if (properties.getPassword() != null) {
            serverConfig.setPassword(RedisPassword.of(properties.getPassword()));
        }
        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }

    /**
     * 暴露 {@link StringRedisTemplate}，方便业务示例直接操作字符串类型的数据。
     *
     * @param factory 上面定义的连接工厂
     * @return 默认序列化为 String 的 Redis 模板
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
