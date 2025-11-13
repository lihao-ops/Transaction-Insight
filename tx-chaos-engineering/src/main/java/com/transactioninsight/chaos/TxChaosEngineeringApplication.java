package com.transactioninsight.chaos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 类说明 / Class Description:
 * 中文：混沌工程模块启动入口，用于故障注入与韧性验证。
 * English: Chaos engineering module entrypoint used for fault injection and resilience validation.
 *
 * 使用场景 / Use Cases:
 * 中文：在非生产环境模拟异常，测试超时与重试策略。
 * English: Simulate anomalies in non-prod to test timeout and retry strategies.
 *
 * 设计目的 / Design Purpose:
 * 中文：提供统一启动以加载故障注入器与切面。
 * English: Unified startup to load fault injectors and aspects.
 */
@SpringBootApplication(scanBasePackages = "com.transactioninsight")
public class TxChaosEngineeringApplication {

    public static void main(String[] args) {
        // 中文：启动容器并加载混沌工程的基础组件
        // English: Boot the container and load base components for chaos engineering
        SpringApplication.run(TxChaosEngineeringApplication.class, args);
    }
}
