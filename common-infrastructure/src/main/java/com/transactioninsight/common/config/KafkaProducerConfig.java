package com.transactioninsight.common.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 生产者公共配置：统一声明序列化器、幂等性与批量参数，保障不同模块发送事务事件时行为一致。
 */
@Configuration
public class KafkaProducerConfig {

    /**
     * 构建一个 {@link ProducerFactory}，并补充演示环境常用的容错参数。
     *
     * @param properties 来自 application.yml 的 Kafka 配置
     * @return 供 {@link KafkaTemplate} 使用的生产者工厂
     */
    @Bean
    public ProducerFactory<String, String> producerFactory(KafkaProperties properties) {
        Map<String, Object> configProps = new HashMap<>(properties.buildProducerProperties());
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        configProps.putIfAbsent(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.putIfAbsent(ProducerConfig.LINGER_MS_CONFIG, 5);
        configProps.putIfAbsent(ProducerConfig.BATCH_SIZE_CONFIG, 32_768);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * 暴露 KafkaTemplate，供业务代码直接发送字符串消息。
     *
     * @param producerFactory 上面的方法创建的工厂
     * @return 已开启幂等性的 KafkaTemplate
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
