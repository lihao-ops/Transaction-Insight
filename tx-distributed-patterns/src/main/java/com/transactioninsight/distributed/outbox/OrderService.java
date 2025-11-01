package com.transactioninsight.distributed.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox 模式中的订单服务：在本地事务内将事件写入 outbox 表，交由异步组件转发。
 */
@Service
public class OrderService {

    private final OutboxMessageRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * @param outboxRepository Outbox 仓储
     * @param objectMapper     Jackson 序列化器
     */
    public OrderService(OutboxMessageRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 在 REQUIRED 事务中写入 Outbox 消息，保证与订单主事务的原子性。
     *
     * @param orderId  订单标识
     * @param payload  订单事件载荷
     */
    @Transactional
    public void createOrder(String orderId, Object payload) {
        try {
            String serialized = objectMapper.writeValueAsString(payload);
            OutboxMessage message = new OutboxMessage(orderId, "OrderCreated", serialized);
            outboxRepository.save(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize order payload", e);
        }
    }
}

/**
 * Outbox 中继器：定时扫描待投递消息并发送到 Kafka，失败时标记为 FAILED 以便后续重试。
 */
@Component
class OutboxRelay {

    private final OutboxMessageRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    OutboxRelay(OutboxMessageRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 每秒扫描 PENDING 消息，发送成功则标记 SENT，否则标记 FAILED。
     */
    @Scheduled(fixedDelay = 1000)
    public void relay() {
        List<OutboxMessage> pending = repository.findByStatus(OutboxStatus.PENDING);
        pending.forEach(message -> {
            try {
                kafkaTemplate.send(message.getEventType(), message.getAggregateId(), message.getPayload());
                message.markSent();
            } catch (Exception ex) {
                message.markFailed();
            }
            // 核心步骤：无论成功或失败都持久化状态，确保下一次扫描可获取最新状态。
            repository.save(message);
        });
    }
}
