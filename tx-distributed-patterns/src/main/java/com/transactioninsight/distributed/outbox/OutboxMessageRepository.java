package com.transactioninsight.distributed.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 类说明 / Class Description:
 * 中文：Outbox 消息仓储，提供 CRUD 与按状态查询待投递消息。
 * English: Outbox message repository offering CRUD and status-based querying of pending messages.
 *
 * 使用场景 / Use Cases:
 * 中文：中继器定时扫描 PENDING 消息并发送到消息系统。
 * English: Relay scans PENDING messages periodically and sends to messaging system.
 *
 * 设计目的 / Design Purpose:
 * 中文：抽象消息访问层，简化中继逻辑并与业务解耦。
 * English: Abstract message access to simplify relay logic and decouple from business.
 */
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    /**
     * 方法说明 / Method Description:
     * 中文：按状态查询所有消息，用于中继器扫描。
     * English: Query all messages by status for relay scanning.
     *
     * 参数 / Parameters:
     * @param status 中文：消息状态 / English: Message status
     *
     * 返回值 / Return:
     * 中文：符合条件的消息列表 / English: List of matched messages
     *
     * 异常 / Exceptions:
     * 中文/英文：无
     */
    List<OutboxMessage> findByStatus(OutboxStatus status);
}
