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
 * 类说明 / Class Description:
 * 中文：Outbox 模式消息实体，持久化事务内产生的事件，供异步组件读取与投递。
 * English: Outbox pattern message entity persisting in-transaction events for async reading and dispatching.
 *
 * 使用场景 / Use Cases:
 * 中文：与主事务同库写入消息，保证事件与业务数据的原子性。
 * English: Write messages in the same DB as main transaction to guarantee atomicity of events and business data.
 *
 * 设计目的 / Design Purpose:
 * 中文：以最小字段集表达可投递事件，支持定时扫描与重试。
 * English: Minimal field set to express dispatchable events, supporting scheduled scans and retries.
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

    /**
     * 方法说明 / Method Description:
     * 中文：JPA 默认构造函数，供框架使用。
     * English: Default JPA constructor for framework usage.
     *
     * 参数 / Parameters: 无
     * 返回值 / Return: 无
     * 异常 / Exceptions: 无
     */
    protected OutboxMessage() {
    }

    /**
     * 方法说明 / Method Description:
     * 中文：构造消息实体，写入聚合根、事件类型与载荷。
     * English: Construct message entity with aggregate ID, event type, and payload.
     *
     * 参数 / Parameters:
     * @param aggregateId 中文：聚合根标识 / English: Aggregate root identifier
     * @param eventType   中文：事件类型 / English: Event type
     * @param payload     中文：JSON 序列化载荷 / English: JSON serialized payload
     *
     * 返回值 / Return:
     * 中文：实体实例 / English: Entity instance
     *
     * 异常 / Exceptions:
     * 中文/英文：无
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

    /**
     * 方法说明 / Method Description:
     * 中文：标记消息为已发送，并刷新更新时间。
     * English: Mark message as SENT and refresh update timestamp.
     *
     * 参数 / Parameters: 无
     * 返回值 / Return: 无
     * 异常 / Exceptions: 无
     */
    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.updatedAt = Instant.now();
    }

    /**
     * 方法说明 / Method Description:
     * 中文：标记消息为发送失败，便于后续重试。
     * English: Mark message as FAILED for subsequent retries.
     *
     * 参数 / Parameters: 无
     * 返回值 / Return: 无
     * 异常 / Exceptions: 无
     */
    public void markFailed() {
        this.status = OutboxStatus.FAILED;
        this.updatedAt = Instant.now();
    }
}
