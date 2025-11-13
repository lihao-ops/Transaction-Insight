package com.transactioninsight.distributed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 类说明 / Class Description:
 * 中文：分布式事务模式示例的启动入口，覆盖 Outbox、TCC 等模式。
 * English: Entry point for distributed transaction patterns including Outbox and TCC.
 *
 * 使用场景 / Use Cases:
 * 中文：演示跨服务一致性与事件驱动集成方案。
 * English: Demonstrate cross-service consistency and event-driven integrations.
 *
 * 设计目的 / Design Purpose:
 * 中文：统一启动以装配 Kafka、仓储与领域服务。
 * English: Unified startup to assemble Kafka, repositories, and domain services.
 */
@SpringBootApplication(scanBasePackages = "com.transactioninsight")
public class DistributedPatternsApplication {

    public static void main(String[] args) {
        // 中文：启动应用以加载分布式模式所需组件
        // English: Start application to load components required for distributed patterns
        SpringApplication.run(DistributedPatternsApplication.class, args);
    }
}
