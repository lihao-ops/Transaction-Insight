# 🧭 Transaction Insight

一个面向生产实践的事务管理实验室，从存储引擎到分布式系统全面覆盖。项目采用多模块 Spring Boot 工作区组织形式，每个主题都可以在独立模块中探索，同时共享数据源、Kafka、Redis 等通用基础设施配置。

---

## 🏗️ 架构总览

```
transaction-insight/
├── common-infrastructure/      # 共享的数据源、Redis、Kafka 与测试基座
├── tx-foundation/              # MySQL 隔离级别、MVCC 可视化及死锁实验
├── tx-spring-core/             # Spring @Transactional 行为与性能实验
├── tx-distributed-patterns/    # 事务外盒、Saga 札记与自研 TCC 框架
├── tx-monitoring/              # 事务指标与慢事务告警
└── tx-chaos-engineering/       # 混沌实验（网络分区、故障注入）
```

每个模块都是一个独立的 Spring Boot 应用（或库），拥有自己的领域模型与测试集。目录结构对应项目简介中的“面试即战力”课程体系，便于直接跳转到感兴趣的场景。

---

## 🛠️ 核心技术栈

| 层次 | 技术 | 说明 |
|------|------|------|
| 语言 | Java 17 | 支持在实验中使用 record、switch 模式匹配、虚拟线程等能力 |
| 框架 | Spring Boot 3.3.x | 为全部模块提供依赖管理 |
| 数据库 | MySQL 8（兼容 H2 测试） | 重现隔离级别、MVCC 与死锁场景 |
| 消息 | Apache Kafka 4 级别配置 | 用于事务外盒消息中继 |
| 缓存 | Redis（Lettuce） | 提供共享缓存与幂等存储 |
| 分布式事务 | 自研 TCC 管理器 + 事务外盒 | 展示补偿型事务机制 |
| 可观测性 | Micrometer + Prometheus | AOP 切面记录事务耗时 |
| 测试 | JUnit 5、Spring Boot Test（H2） | 无需 Docker 即可复现实验 |

---

## 📦 模块亮点

### `common-infrastructure`
与题目参考 YAML 配置对齐的数据源、Redis、Kafka 公共配置。所有服务都引入该模块以保持连接参数一致。

### `tx-foundation`
深入 InnoDB 内部机制的实践：
- 在 `READ_UNCOMMITTED` 隔离级别下使用两个物理连接验证脏读。
- 借助可视化服务捕获 MVCC 快照。
- 提供刻意交叉加锁的死锁复现实用工具。

### `tx-spring-core`
聚焦 Spring 事务语义：
- `PropagationLabService` 基准对比 `REQUIRED` 与 `REQUIRES_NEW` 批处理。
- 自调用实验揭示基于代理的事务失效现象。
- 测试默认使用 H2，保证快速反馈。

### `tx-distributed-patterns`
覆盖现代微服务事务模式：
- 具备定时中继到 Kafka 的事务外盒实现。
- 内置基于内存账户示例的轻量级 TCC 事务管理器。
- Spring Boot 测试覆盖消息持久化与 TCC 补偿流程。

### `tx-monitoring`
基于 AspectJ 的 Micrometer 切面，记录所有 `@Transactional` 边界的执行耗时，可直接对接 Prometheus / Grafana。

### `tx-chaos-engineering`
为 TCC confirm 阶段准备的混沌实验脚手架。示例测试默认禁用，说明如何与外部故障注入工具（tc、Chaos Mesh 等）联动。

---

## 🚀 快速开始

```bash
# 编译全部模块
mvn clean verify

# 仅运行某个模块的测试（示例：foundation）
mvn -pl tx-foundation test

# 启动分布式事务模式模块进行手工探索
mvn -pl tx-distributed-patterns spring-boot:run
```

`common-infrastructure` 中的默认 `application.yml` 与题目提供的面试级配置一致。请在本地运行时通过标准的 Spring Boot 属性覆盖方式调整凭据或主机地址。

---

## 🧪 精选实验：脏读演示

```java
try (Connection writer = dataSource.getConnection();
     Connection reader = dataSource.getConnection()) {
    writer.setAutoCommit(false);
    reader.setAutoCommit(false);

    writer.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
    reader.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);

    updateBalance(writer, 1L, new BigDecimal("1000"));
    BigDecimal dirtyBalance = queryBalance(reader, 1L); // -> 1000（脏读）

    writer.rollback();
    BigDecimal cleanBalance = queryBalance(reader, 1L); // -> 500（快照）
}
```

该片段由测试驱动，位于 `tx-foundation` 模块，可通过 `mvn -pl tx-foundation test` 直接运行。

---

## 📈 可观测性

监控模块内置 `TransactionMetricsAspect`，会按方法签名打标签记录 Micrometer 定时器。只需在应用层添加对应的注册器依赖即可对接 Prometheus。

---

## 🧭 路线图

- [x] MySQL 隔离级别验证套件
- [x] Spring 传播机制性能基准
- [x] 事务外盒实现
- [x] 内存型 TCC 框架原型
- [ ] 完整的 Saga 编排示例
- [ ] 集成 Seata AT/TCC 模块
- [ ] 基于 Docker 编排的自动化混沌场景

---

## 📄 许可证

MIT 许可证 © 2024 Transaction Insight 团队
