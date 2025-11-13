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
 * 类说明 / Class Description:
 * 中文：Outbox 模式订单服务，在本地事务内写入事件到 outbox 表，供异步中继转发。
 * English: Outbox pattern order service writing events to outbox table in local transaction for async relay.
 *
 * 使用场景 / Use Cases:
 * 中文：保障订单主事务与事件写入原子性，以便可靠投递。
 * English: Ensure atomicity of order transaction and event write for reliable dispatch.
 *
 * 设计目的 / Design Purpose:
 * 中文：解耦事务提交与消息发送，通过数据库表实现最终一致。
 * English: Decouple transaction commit from message sending to achieve eventual consistency via DB table.
 */
@Service
public class OrderService {

    private final OutboxMessageRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * 方法说明 / Method Description:
     * 中文：构造服务并注入仓储与序列化器。
     * English: Construct service with injected repository and serializer.
     *
     * 参数 / Parameters:
     * @param outboxRepository 中文：Outbox 仓储 / English: Outbox repository
     * @param objectMapper     中文：Jackson 序列化器 / English: Jackson serializer
     *
     * 返回值 / Return:
     * 中文：服务实例 / English: Service instance
     *
     * 异常 / Exceptions:
     * 中文/英文：无
     */
    public OrderService(OutboxMessageRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 方法说明 / Method Description:
     * 中文：在 REQUIRED 事务中写入 Outbox 消息，保证与订单主事务的原子性。
     * English: Write Outbox message in REQUIRED transaction ensuring atomicity with main order transaction.
     *
     * 参数 / Parameters:
     * @param orderId  中文：订单标识 / English: Order identifier
     * @param payload  中文：订单事件载荷 / English: Order event payload
     *
     * 返回值 / Return:
     * 中文：无 / English: None
     *
     * 异常 / Exceptions:
     * 中文/英文：序列化失败抛 IllegalStateException
     *
     * 逻辑概述 / Logic Overview:
     * 中文：序列化载荷后持久化 Outbox 消息，交由中继定时转发。
     * English: Serialize payload then persist Outbox message for periodic relay forwarding.
     */
    @Transactional
    public void createOrder(String orderId, Object payload) {
        try {
            // 中文：序列化事件载荷为 JSON 文本
            // English: Serialize event payload into JSON text
            String serialized = objectMapper.writeValueAsString(payload);
            // 中文：构建 Outbox 消息实体，写入订单事件
            // English: Build Outbox message entity for order event
            OutboxMessage message = new OutboxMessage(orderId, "OrderCreated", serialized);
            // 中文：持久化消息，保证与业务事务一起提交
            // English: Persist message to commit with business transaction
            outboxRepository.save(message);
        } catch (JsonProcessingException e) {
            // 中文：序列化失败转换为非法状态异常
            // English: Convert serialization failure to IllegalStateException
            throw new IllegalStateException("Unable to serialize order payload", e);
        }
    }
}

/**
 * 类说明 / Class Description:
 * 中文：Outbox 中继器，定时扫描待投递消息并发送到 Kafka，失败标记为 FAILED 以便重试。
 * English: Outbox relay scanning pending messages periodically and sending to Kafka, marking FAILED on errors for retries.
 *
 * 使用场景 / Use Cases:
 * 中文：解耦消息投递与业务事务，保障最终一致性。
 * English: Decouple message delivery from business transaction, ensuring eventual consistency.
 *
 * 设计目的 / Design Purpose:
 * 中文：通过定时任务实现可靠投递与错误重试。
 * English: Achieve reliable delivery and error retries via scheduled job.
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
     * 方法说明 / Method Description:
     * 中文：每秒扫描 PENDING 消息，发送成功标记 SENT，失败标记 FAILED 并持久化状态。
     * English: Scan PENDING messages each second, mark SENT on success, FAILED on failure, and persist status.
     *
     * 参数 / Parameters: 无
     * 返回值 / Return: 无
     * 异常 / Exceptions: 中文/英文：发送异常将记录并转换为失败状态
     */
    @Scheduled(fixedDelay = 1000)
    public void relay() {
        // 中文：查询所有待投递消息
        // English: Query all pending messages for dispatch
        List<OutboxMessage> pending = repository.findByStatus(OutboxStatus.PENDING);
        // 中文：逐条发送并更新状态
        // English: Send each message and update its status
        pending.forEach(message -> {
            try {
                // 中文：发送到 Kafka 并标记成功
                // English: Send to Kafka and mark as success
                kafkaTemplate.send(message.getEventType(), message.getAggregateId(), message.getPayload());
                message.markSent();
            } catch (Exception ex) {
                // 中文：发生异常则标记失败以便重试
                // English: Mark as failed on exception for retry
                message.markFailed();
            }
            // 中文：持久化状态，确保下一次扫描获取最新信息
            // English: Persist status to ensure latest info on next scan
            repository.save(message);
        });
    }
}
