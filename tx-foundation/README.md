# tx-foundation 模块使用说明

## 模块功能
- 演示 MySQL 隔离级别（特别是 READ_UNCOMMITTED）的脏读现象。
- 提供 MVCC 快照可视化服务和死锁复现工具类，辅助面试或授课演示。
- 复用 `common-infrastructure` 提供的基础配置，专注于数据库层面的事务机制。

## 核心案例：MySQL 事务原理验证实验
我们在 [`src/main/resources/sql/mysql-transaction-lab.sql`](src/main/resources/sql/mysql-transaction-lab.sql) 中整理了一份**开箱即用**的实验手册，覆盖 ACID 特性、锁机制和 MVCC 的关键现象。按照文件中的步骤，在两个 MySQL 会话窗口即可复现以下场景：

1. 原子性、回滚与一致性校验（转账示例）
2. 持久性验证（提交后重启服务）
3. 各种隔离级别下的脏读 / 不可重复读 / 幻读
4. MVCC 快照读 vs. 当前读
5. 行锁、间隙锁与死锁检测

> ✅ 该脚本默认使用极简的 `account` 表结构，只需导入并按顺序执行注释中的命令，就能快速在课堂、直播或面试演示中验证事务核心概念。

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
