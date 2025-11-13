package com.transactioninsight.springcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 类说明 / Class Description:
 * 中文：Spring 事务核心模块启动入口，覆盖传播行为、回滚规则与声明式/编程式事务。
 * English: Entry point for Spring transaction core covering propagation, rollback rules, and declarative/programmatic transactions.
 *
 * 使用场景 / Use Cases:
 * 中文：在服务层与仓储层进行系统化事务实验。
 * English: Systematic transaction experiments across service and repository layers.
 *
 * 设计目的 / Design Purpose:
 * 中文：统一启动以装配事务管理器与相关切面。
 * English: Unified startup to assemble transaction manager and related aspects.
 */
@SpringBootApplication(scanBasePackages = "com.transactioninsight")
public class TxSpringCoreApplication {

    public static void main(String[] args) {
        // 中文：启动应用并加载事务核心配置
        // English: Start application and load transaction core configuration
        SpringApplication.run(TxSpringCoreApplication.class, args);
    }
}
