非常好，小李 👍
你这个项目“**Transaction Insight**”定位清晰：既是**Spring Boot 事务学习实践项目**，又涵盖了从 MySQL 本地事务到分布式事务的完整体系。
下面我帮你生成一份 **专业版 README.md**，适合直接放进你的 GitHub 仓库首页。

---

```markdown
# 🧩 Transaction Insight

> A Spring Boot–based lab for mastering transaction mechanisms —  
> from MySQL local transactions to Spring @Transactional and distributed transaction patterns.

---

## 🚀 Overview

**Transaction Insight** 是一个基于 **Spring Boot** 的事务机制全景实验项目，  
旨在通过实践深入理解从 **数据库层事务** 到 **Spring 框架事务**，再到 **分布式事务** 的底层原理与实现方式。  

本项目适合想要：
- 系统掌握 **事务的 ACID 特性与隔离级别**；
- 理解 **@Transactional 注解** 在 Spring 容器中的代理与传播行为；
- 亲手实现 **消息表、XA、TCC 等分布式事务模型**；
- 在面试中展示扎实事务掌控能力的开发者。

---

## 🧠 Learning Goals

| 模块 | 学习目标 |
|------|-----------|
| 🧩 `tx-local` | 掌握 MySQL 原生事务控制（commit / rollback / isolation level） |
| ⚙️ `tx-spring` | 理解 Spring 声明式与编程式事务、传播机制与异常回滚规则 |
| 🌐 `tx-distributed` | 实践分布式事务：消息补偿、XA 两阶段提交、TCC 模型 |

---

## 📁 Project Structure

```

transaction-insight/
├── tx-local/           # MySQL 原生事务实验（JDBC 手动提交与隔离级别测试）
├── tx-spring/          # Spring 声明式与编程式事务（@Transactional / TransactionTemplate）
├── tx-distributed/     # 分布式事务实验（消息表、XA、TCC）
├── common/             # 公共模块（DTO、工具类、配置）
└── README.md           # 项目说明文件

````

---

## ⚙️ Tech Stack

| 技术 | 用途 |
|------|------|
| **Spring Boot 3.5.x** | 项目主框架 |
| **MySQL 8.x** | 本地事务与隔离级别实验 |
| **Spring Data / MyBatis** | ORM 与事务集成 |
| **HikariCP** | 数据源与连接池 |
| **RabbitMQ / Kafka** | 消息驱动分布式事务 |
| **Seata / Atomikos** | TCC / XA 分布式事务管理 |
| **Docker Compose** | 一键启动数据库与消息中间件 |
| **JUnit 5** | 单元测试事务行为 |

---

## 🔍 Key Topics

- ✅ MySQL 事务四大特性（ACID）  
- ✅ 各隔离级别下的并发问题（脏读、不可重复读、幻读）  
- ✅ Spring `@Transactional` 原理（AOP 代理、传播行为）  
- ✅ 回滚策略：受检异常与非受检异常的差异  
- ✅ 编程式事务控制（`TransactionTemplate`、`PlatformTransactionManager`）  
- ✅ 分布式事务：消息表、TCC、XA 两阶段提交  
- ✅ 补偿与幂等性设计

---

## 🧪 Example: Local Transaction Demo

```java
@Transactional
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    Account from = accountRepository.findById(fromId).get();
    Account to = accountRepository.findById(toId).get();

    from.decrease(amount);
    to.increase(amount);

    accountRepository.save(from);
    accountRepository.save(to);
}
````

> 实验目标：
> 在不同隔离级别下模拟转账并发问题，分析事务边界与传播影响。

---

## 🧭 Run Locally

```bash
# 克隆仓库
git clone https://github.com/lihao-ops/Transaction-Insight.git

cd Transaction-Insight

# 启动 MySQL 与 RabbitMQ（如需分布式事务实验）
docker-compose up -d

# 启动 Spring Boot 应用
mvn spring-boot:run
```

---

## 📊 Roadmap

* [x] MySQL 本地事务控制实验
* [x] Spring 声明式事务传播机制
* [ ] 分布式事务（消息补偿模型）
* [ ] Seata TCC 模型实践
* [ ] 性能与一致性对比分析报告

---

## 📚 References

* 《深入理解 Java 虚拟机（第三版）》
* 《Spring 实战（第六版）》
* 阿里巴巴分布式事务规范（GTS / Seata）
* MySQL 官方文档 — Transaction and Isolation Levels
* Spring Framework Docs — Transaction Management

---

## 🧩 Author

**Li Hao（小李）**

> Backend Engineer @ Wind Information
> Passionate about high-concurrency architectures, JVM internals, and distributed systems.

📬 GitHub: [@lihao-ops](https://github.com/lihao-ops)

---

## 🧱 License

This project is licensed under the MIT License.

---

```

---
方便后续在分布式事务阶段（消息补偿 / TCC）直接跑实验？
```
