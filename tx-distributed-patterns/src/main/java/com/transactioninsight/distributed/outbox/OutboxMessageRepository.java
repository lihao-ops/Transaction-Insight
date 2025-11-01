package com.transactioninsight.distributed.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findByStatus(OutboxStatus status);
}
