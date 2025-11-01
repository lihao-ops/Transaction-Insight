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

@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(RedisProperties properties) {
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

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
