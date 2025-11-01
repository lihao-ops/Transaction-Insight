package com.transactioninsight.distributed.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Outbox 模式中的消息实体，负责持久化事务内产生的事件，供异步投递组件读取。
 */
@Entity
@Table(name = "outbox_message")
public class OutboxMessage {

    /** 主键 ID。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务聚合根标识，例如订单号。 */
    @Column(nullable = false)
    private String aggregateId;

    /** 事件类型，如 OrderCreated。 */
    @Column(nullable = false)
    private String eventType;

    /** 序列化后的事件载荷。 */
    @Column(nullable = false, length = 4000)
    private String payload;

    /** 消息状态，记录投递结果。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    /** 创建时间，用于定时扫描排序。 */
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** 更新时间，伴随状态变化刷新。 */
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    /** JPA 默认构造函数。 */
    protected OutboxMessage() {
    }

    /**
     * @param aggregateId 聚合根标识
     * @param eventType   事件类型
     * @param payload     JSON 序列化后的载荷
     */
    public OutboxMessage(String aggregateId, String eventType, String payload) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
    }

    /**
     * @return 主键 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * @return 聚合根标识
     */
    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * @return 事件类型
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * @return 事件载荷
     */
    public String getPayload() {
        return payload;
    }

    /**
     * @return 当前状态
     */
    public OutboxStatus getStatus() {
        return status;
    }

    /** 将消息标记为已发送，同时刷新更新时间。 */
    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.updatedAt = Instant.now();
    }

    /** 将消息标记为发送失败，便于后续重试。 */
    public void markFailed() {
        this.status = OutboxStatus.FAILED;
        this.updatedAt = Instant.now();
    }
}
