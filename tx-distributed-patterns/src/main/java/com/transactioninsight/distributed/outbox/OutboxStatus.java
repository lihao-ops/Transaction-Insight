package com.transactioninsight.distributed.outbox;

enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
