# tx-foundation 模块使用说明

## 模块功能
- 演示 MySQL 隔离级别（特别是 READ_UNCOMMITTED）的脏读现象。
- 提供 MVCC 快照可视化服务和死锁复现工具类，辅助面试或授课演示。
- 复用 `common-infrastructure` 提供的基础配置，专注于数据库层面的事务机制。

## 启动与运行
- 运行集成测试（默认使用 Testcontainers，可根据需要改为本地 MySQL）：
  ```bash
  # 使用本地 MySQL 避免 Docker：提前创建库并导入 docs/test-schemas/tx-foundation.sql
  mvn -pl tx-foundation test \
    -Dspring.test.container.enabled=false \
    -Dspring.datasource.url=jdbc:mysql://localhost:3306/transaction_test \
    -Dspring.datasource.username=root \
    -Dspring.datasource.password=secret
  ```
- 启动 Spring Boot 应用以便调试服务类：
  ```bash
  mvn -pl tx-foundation spring-boot:run
  ```
- 若仍希望使用 Testcontainers，需要本地 Docker 环境并保持联网。

## 依赖关系
- 依赖 `common-infrastructure` 获取数据源、Redis、Kafka 等配置。
- 被其他模块引用其实体（例如 TCC 演示），但自身不依赖业务模块。
