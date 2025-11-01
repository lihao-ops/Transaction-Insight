package com.transactioninsight.distributed.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Outbox 消息仓储，既提供基本 CRUD，又支持按状态查询待投递消息。
 */
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    /**
     * @param status 消息状态
     * @return 指定状态的所有消息列表
     */
    List<OutboxMessage> findByStatus(OutboxStatus status);
}
