package com.transactioninsight.foundation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 类说明 / Class Description:
 * 中文：基础事务实验模块的启动入口，聚焦隔离级别、锁与并发示例。
 * English: Entry point for foundational transaction experiments focusing on isolation, locks, and concurrency.
 *
 * 使用场景 / Use Cases:
 * 中文：本地/实验环境验证事务行为与数据一致性。
 * English: Validate transactional behaviors and data consistency in local/lab environments.
 *
 * 设计目的 / Design Purpose:
 * 中文：提供统一的启动方式，加载数据源与仓储层，便于观察事务效果。
 * English: Provide unified startup, loading DataSource and repository layer to observe transactional effects.
 */
@MapperScan("com.transactioninsight.foundation.deadlock.order_inventory.mapper")  // 只扫描 MyBatis Mapper
@EnableJpaRepositories("com.transactioninsight.foundation.model")  // 只扫描 JPA Repository
@SpringBootApplication(scanBasePackages = "com.transactioninsight")  // 默认扫描基础包
public class TransactionFoundationApplication {

    public static void main(String[] args) {
        // 中文：启动嵌入式容器并加载事务相关自动配置
        // English: Start embedded container and load transaction auto configurations
        SpringApplication.run(TransactionFoundationApplication.class, args);
    }
}
