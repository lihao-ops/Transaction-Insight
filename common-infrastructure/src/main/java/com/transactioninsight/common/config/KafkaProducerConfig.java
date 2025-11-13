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
 * 类说明 / Class Description:
 * 中文：Kafka 生产者统一配置，规范序列化、幂等性与批量策略以保障消息可靠性。
 * English: Unified Kafka producer configuration standardizing serialization, idempotence, and batching for reliability.
 *
 * 使用场景 / Use Cases:
 * 中文：在分布式模式模块发送事件消息，确保（至少一次）与幂等语义。
 * English: Send event messages in distributed patterns with at-least-once and idempotent semantics.
 *
 * 设计目的 / Design Purpose:
 * 中文：减少重复配置，统一可靠性参数，提升生产者行为一致性。
 * English: Reduce duplication, unify reliability parameters, and improve consistent producer behavior.
 */
@Configuration
public class KafkaProducerConfig {

    /**
     * 方法说明 / Method Description:
     * 中文：创建 ProducerFactory，并补齐关键可靠性参数（acks、幂等性、批量等）。
     * English: Create ProducerFactory and complete key reliability params (acks, idempotence, batching).
     *
     * 参数 / Parameters:
     * @param properties 中文说明：从 application.yml 读取的 Kafka 属性
     *                   English description: Kafka properties read from application.yml
     *
     * 返回值 / Return:
     * 中文说明：生产者工厂，供 KafkaTemplate 复用
     * English description: ProducerFactory for KafkaTemplate reuse
     *
     * 异常 / Exceptions:
     * 中文/英文：当 bootstrap-servers 未配置时可能导致运行时失败
     */
    @Bean
    public ProducerFactory<String, String> producerFactory(KafkaProperties properties) {
        // 中文：复制默认配置并完善关键可靠性参数
        // English: Copy default properties and enrich key reliability parameters
        Map<String, Object> configProps = new HashMap<>(properties.buildProducerProperties());
        // 中文：统一字符串序列化
        // English: Standardize string serialization
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // 中文：可靠性与幂等性保障
        // English: Reliability and idempotence guarantees
        configProps.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        configProps.putIfAbsent(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // 中文：批量与延迟策略
        // English: Batching and latency strategy
        configProps.putIfAbsent(ProducerConfig.LINGER_MS_CONFIG, 5);
        configProps.putIfAbsent(ProducerConfig.BATCH_SIZE_CONFIG, 32_768);
        // 中文：返回工厂实例
        // English: Return factory instance
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * 方法说明 / Method Description:
     * 中文：创建 KafkaTemplate，提供统一的消息发送入口。
     * English: Create KafkaTemplate providing a unified message sending entrypoint.
     *
     * 参数 / Parameters:
     * @param producerFactory 中文说明：上面创建的 ProducerFactory
     *                        English description: The ProducerFactory created above
     *
     * 返回值 / Return:
     * 中文说明：KafkaTemplate（支持字符串消息）
     * English description: KafkaTemplate supporting string messages
     *
     * 异常 / Exceptions:
     * 中文/英文：无（构造失败由 Spring 抛出运行时异常）
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        // 中文：基于生产者工厂创建模板供业务使用
        // English: Create template from producer factory for business use
        return new KafkaTemplate<>(producerFactory);
    }
}
