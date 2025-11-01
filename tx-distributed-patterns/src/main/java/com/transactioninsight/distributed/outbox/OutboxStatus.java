package com.transactioninsight.distributed.outbox;

/**
 * Outbox 消息状态枚举，用于标记消息投递链路的执行情况。
 */
enum OutboxStatus {
    /** 待投递。 */
    PENDING,
    /** 已成功发送至外部系统。 */
    SENT,
    /** 投递失败，等待重试。 */
    FAILED
}
