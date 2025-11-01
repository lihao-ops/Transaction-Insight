# tx-foundation 模块使用说明

## 模块功能
- 演示 MySQL 隔离级别（特别是 READ_UNCOMMITTED）的脏读现象。
- 提供 MVCC 快照可视化服务和死锁复现工具类，辅助面试或授课演示。
- 复用 `common-infrastructure` 提供的基础配置，专注于数据库层面的事务机制。

## 启动与运行
- 运行集成测试（默认使用 H2 的 MySQL 模式，无需 Docker 或外部数据库）：
  ```bash
  mvn -pl tx-foundation test
  ```
- 如需对接真实 MySQL，可在命令行传入标准 Spring 属性覆盖数据源。
- 启动 Spring Boot 应用以便调试服务类：
  ```bash
  mvn -pl tx-foundation spring-boot:run
  ```

## 依赖关系
- 依赖 `common-infrastructure` 获取数据源、Redis、Kafka 等配置。
- 被其他模块引用其实体（例如 TCC 演示），但自身不依赖业务模块。
