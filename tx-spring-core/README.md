# tx-spring-core 模块使用说明

## 模块功能
- 探索 Spring `@Transactional` 的传播行为、批量写入性能以及自调用失效等典型问题。
- 暴露 `PropagationLabService` 供外部控制器或命令行 Runner 调用。
- 使用 JPA/H2 快速持久化实验记录，方便进行对比分析。
- 新增基于 Testcontainers + MySQL 的 ACID 验证实验，覆盖原子性、一致性、隔离性、持久性。

## 启动与运行
- 运行模块测试：
  ```bash
  mvn -pl tx-spring-core test
  ```
- 启动应用并通过调试或命令行调用服务：
  ```bash
  mvn -pl tx-spring-core spring-boot:run
  ```
- 所有测试默认使用内存数据库，无需额外依赖；如需接入 MySQL，可调整 `application.yml` 并导入 `docs/test-schemas/tx-spring-core.sql`。
- ACID 集成测试自动拉起 Testcontainers MySQL 实例，可通过以下命令验证事务特性：
  ```bash
  mvn -pl tx-spring-core -Dtest='*IntegrationTest' test
  ```

## 依赖关系
- 依赖 `common-infrastructure` 获取统一的数据源和 Redis、Kafka Bean。
- 可与 `tx-monitoring` 配合，观察事务传播实验的 Micrometer 指标。
