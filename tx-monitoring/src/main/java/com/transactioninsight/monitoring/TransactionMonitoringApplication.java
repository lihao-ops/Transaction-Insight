package com.transactioninsight.monitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 类说明 / Class Description:
 * 中文：事务监控模块启动入口，提供指标与可观测性支撑。
 * English: Entry point for transaction monitoring module providing metrics and observability support.
 *
 * 使用场景 / Use Cases:
 * 中文：采集并暴露监控指标，分析事务性能与稳定性。
 * English: Collect/expose metrics to analyze transaction performance and stability.
 *
 * 设计目的 / Design Purpose:
 * 中文：以轻量方式集成 Micrometer 指标采集。
 * English: Lightly integrate Micrometer metrics collection.
 */
@SpringBootApplication(scanBasePackages = "com.transactioninsight")
public class TransactionMonitoringApplication {

    public static void main(String[] args) {
        // 中文：启动监控模块并注册默认的指标采集器
        // English: Start monitoring module and register default metric collectors
        SpringApplication.run(TransactionMonitoringApplication.class, args);
    }
}
