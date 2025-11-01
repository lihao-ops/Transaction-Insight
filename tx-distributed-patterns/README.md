# tx-distributed-patterns 模块使用说明

## 模块功能
- 实现事务型 Outbox 模式，展示消息可靠投递的全流程（持久化 + 定时转发）。
- 提供简化版 TCC 框架及账户示例，用于演示跨资源补偿事务。
- 作为分布式事务实验的主舞台，可与 Chaos 模块联动进行故障注入。

## 启动与运行
- 运行单元测试（Kafka 通过 MockBean，无需真实集群）：
  ```bash
  mvn -pl tx-distributed-patterns test
  ```
- 启动应用，观察 Outbox 中继的日志输出：
  ```bash
  mvn -pl tx-distributed-patterns spring-boot:run \
    -Dspring.kafka.bootstrap-servers=localhost:9092 \
    -Dspring.datasource.url=jdbc:mysql://localhost:3306/transaction_test
  ```
- 若需完整体验 Outbox，请先导入 `docs/test-schemas/tx-distributed-patterns.sql` 并准备 Kafka 集群。

## 依赖关系
- 依赖 `common-infrastructure` 共享 Kafka、数据源配置。
- 可被 `tx-chaos-engineering` 引用以进行 TCC 故障注入实验。
