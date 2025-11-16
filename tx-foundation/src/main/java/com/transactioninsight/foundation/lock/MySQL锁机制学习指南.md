# MySQL锁机制学习指南

## 📚 项目概述

本项目提供了7个完整的测试用例，帮助你深入理解MySQL的锁机制，包括：
- ✅ 写冲突场景
- ✅ 排他锁(X锁)与共享锁(S锁)
- ✅ 死锁复现与检测
- ✅ 幻读与间隙锁
- ✅ 行锁与表锁对比
- ✅ 乐观锁与悲观锁

## 🚀 快速开始

### 1. 环境准备

**MySQL配置要求：**
```sql
-- 检查事务隔离级别（建议使用REPEATABLE-READ）
SELECT @@transaction_isolation;

-- 检查InnoDB引擎
SHOW ENGINES;

-- 创建数据库
CREATE DATABASE lock_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE transaction_study;
```

**创建表结构：**
```sql
CREATE TABLE account_lock (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '账户的唯一标识符',
                              account_no VARCHAR(50) NOT NULL UNIQUE COMMENT '账户编号，必须唯一',
                              balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00 COMMENT '账户余额，精度为 15 位数字，其中小数部分为 2 位',
                              VERSION BIGINT DEFAULT 0 COMMENT '乐观锁版本号，用于实现乐观锁机制',
                              INDEX idx_account_no (account_no) COMMENT '根据账户编号建立索引，提高查询效率'
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COMMENT='账户表lock，用于存储账户信息，包括账户编号、余额和版本号';

-- 插入测试数据
INSERT INTO account (account_no, balance, VERSION) VALUES
                                                       ('ACC001', 1000.00, 0),
                                                       ('ACC002', 2000.00, 0);
```

### 2. 配置项目

**application.yml:**
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/transaction_study?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: Q836184425
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
#Hibernate 已自动为 MySQL 选择了合适的方言，且不需要显式指定 hibernate.dialect 属性
#        dialect: org.hibernate.dialect.MySQLDialect

logging:
  level:
    com.example.lock: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### 3. 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试
mvn test -Dtest=MySQLLockMechanismTests#testWriteConflict_NoLock
```

## 📖 测试场景详解

### 场景1：写冲突（无锁）⚠️

**目的：** 理解为什么MVCC无法解决写冲突

**现象分析：**
```
[线程1] 读取到余额: 1000.00
[线程2] 读取到余额: 1000.00  ← 两个线程读到相同值
[线程1] 更新余额: 1000.00 -> 1100.00
[线程2] 更新余额: 1000.00 -> 1100.00  ← 覆盖了线程1的修改
最终余额: 1100.00 (预期1200.00) ← 丢失更新！
```

**关键点：**
- MVCC只保证读的一致性（每个事务看到自己的快照）
- 无法防止"丢失更新"问题
- 最后提交的事务会覆盖之前的修改

---

### 场景2：排他锁解决写冲突 ✅

**目的：** 使用`SELECT ... FOR UPDATE`防止写冲突

**SQL执行：**
```sql
-- 线程1执行
BEGIN;
SELECT * FROM account WHERE account_no = 'ACC001' FOR UPDATE; -- 获取排他锁
-- 更新操作
COMMIT; -- 释放锁

-- 线程2执行（会阻塞在SELECT）
BEGIN;
SELECT * FROM account WHERE account_no = 'ACC001' FOR UPDATE; -- 等待线程1释放锁
```

**现象分析：**
```
[线程1] 成功获取排他锁，读取到余额: 1000.00
[线程2] 尝试获取排他锁... (阻塞中)
[线程1] 更新余额完成: 1100.00
[线程1] 事务提交，释放锁
[线程2] ✓ 成功获取排他锁 (等待了1000ms)，余额: 1100.00
[线程2] 更新余额完成: 1200.00
最终余额: 1200.00 ✓ 正确！
```

---

### 场景3：共享锁 vs 排他锁 🔐

**目的：** 理解锁的兼容性矩阵

**锁兼容性：**
```
        | 共享锁(S) | 排他锁(X)
--------|-----------|----------
共享锁(S) |    ✓     |    ✗
排他锁(X) |    ✗     |    ✗
```

**现象分析：**
```
[线程1-共享锁] ✓ 成功获取共享锁
[线程2-共享锁] ✓ 成功获取共享锁  ← 多个共享锁可以共存
[线程3-排他锁] 尝试获取排他锁... (阻塞中)
[线程1-共享锁] 释放共享锁
[线程2-共享锁] 释放共享锁
[线程3-排他锁] ✓ 成功获取排他锁 (等待了2500ms)
```

**应用场景：**
- 共享锁：用于读操作，`SELECT ... LOCK IN SHARE MODE`
- 排他锁：用于写操作，`SELECT ... FOR UPDATE`

---

### 场景4：死锁复现 💀

**目的：** 理解死锁产生条件和MySQL的死锁检测

**死锁条件：**
1. 互斥条件：资源不能被多个事务共享
2. 持有并等待：已持有锁，还在等待其他锁
3. 不可剥夺：锁不能被强制释放
4. 循环等待：形成锁的等待环路

**执行时序：**
```
时间 | 线程1                  | 线程2
-----|------------------------|------------------------
t1   | 锁定 ACC001            |
t2   |                        | 锁定 ACC002
t3   | 尝试锁定 ACC002 (等待) |
t4   |                        | 尝试锁定 ACC001 (等待)
t5   | ← 死锁！MySQL检测到并回滚其中一个事务
```

**现象分析：**
```
[线程1] ✓ 成功锁定源账户 ACC001
[线程2] ✓ 成功锁定源账户 ACC002
[线程1] 尝试锁定目标账户 ACC002... (阻塞)
[线程2] 尝试锁定目标账户 ACC001... (阻塞)
[线程2] 发生异常: Deadlock found when trying to get lock
[线程1] ✓ 成功锁定目标账户 ACC002
[线程1] 转账完成
```

**查看死锁信息：**
```sql
SHOW ENGINE INNODB STATUS;
-- 查找 LATEST DETECTED DEADLOCK 部分
```

---

### 场景5：幻读与间隙锁 👻

**目的：** 理解幻读问题和间隙锁的作用

**什么是幻读？**
- 同一事务中，两次相同的范围查询返回不同的结果集
- 原因：其他事务插入了新数据

**间隙锁（Gap Lock）：**
- 锁定索引记录之间的"间隙"
- 防止其他事务在间隙中插入数据
- 只在REPEATABLE READ隔离级别下生效

**现象分析：**
```
[线程1-查询] 第一次范围查询：余额 > 500
[线程1-查询] - 查询到: ACC001, 余额: 1000.00
[线程1-查询] - 查询到: ACC002, 余额: 2000.00
[线程2-插入] 尝试插入新账户 ACC003, 余额600 (阻塞中...)
-- 线程2被间隙锁阻塞，无法插入
[线程1-查询] 第二次范围查询：余额 > 500
[线程1-查询] - 查询到: ACC001, 余额: 1000.00
[线程1-查询] - 查询到: ACC002, 余额: 2000.00
-- 仍然是2条记录，没有幻读！
[线程1-查询] 查询完成，释放间隙锁
[线程2-插入] ✓ 插入成功 (等待了3000ms)
```

**锁的范围：**
```
余额范围：(500, +∞)
间隙锁锁定：(500, 1000), (1000, 2000), (2000, +∞)
```

---

### 场景6：行锁 vs 表锁 🎯

**目的：** 对比行锁和表锁的并发性能

**行锁特点：**
- 锁定粒度小，并发度高
- 只锁定操作的行，不影响其他行
- InnoDB默认使用行锁

**现象分析：**
```
[线程1-修改ACC001] 成功获取排他锁，余额: 1000.00
[线程2-修改ACC002] 成功获取排他锁，余额: 2000.00
-- 两个线程同时执行，因为锁定的是不同的行！
[线程1-修改ACC001] 更新余额完成: 1100.00
[线程2-修改ACC002] 更新余额完成: 2200.00
```

**表锁特点：**
- 锁定整个表，并发度低
- 适用于批量操作
- MyISAM引擎只支持表锁

---

### 场景7：乐观锁 vs 悲观锁 🎲

**目的：** 对比两种锁策略的适用场景

**悲观锁：**
- 假设冲突一定会发生，提前加锁
- 使用数据库锁机制（SELECT ... FOR UPDATE）
- 适用于写操作频繁的场景

**乐观锁：**
- 假设冲突很少发生，不提前加锁
- 使用版本号机制（version字段）
- 适用于读操作频繁的场景

**现象分析：**
```
[线程1] 读取到余额: 1000.00, version: 0
[线程2] 读取到余额: 1000.00, version: 0
-- 两个线程都读取到相同版本
[线程1] ✓ 更新成功，version: 0 -> 1
[线程2] 乐观锁更新失败: Row was updated by another transaction
-- 线程2提交时发现version已经变化，更新失败
```

**JPA自动检查版本：**
```java
@Version
private Long version;
// JPA会自动在UPDATE语句中添加WHERE version = ?
```

---

## 🔍 监控和调试

### 查看当前锁情况

```sql
-- 查看正在执行的事务
SELECT * FROM information_schema.INNODB_TRX;

-- 查看锁等待情况
SELECT * FROM information_schema.INNODB_LOCK_WAITS;

-- 查看锁信息
SELECT * FROM performance_schema.data_locks;

-- 查看锁等待
SELECT * FROM performance_schema.data_lock_waits;
```

### 死锁分析

```sql
-- 查看最近的死锁信息
SHOW ENGINE INNODB STATUS;
```

### 开启日志

```sql
-- 开启通用查询日志
SET GLOBAL general_log = 'ON';
SET GLOBAL log_output = 'TABLE';
SELECT * FROM mysql.general_log ORDER BY event_time DESC LIMIT 100;
```

---

## 💡 关键要点总结

### MVCC vs 锁机制

| 特性 | MVCC | 锁机制 |
|------|------|--------|
| 主要解决 | 读的并发性 | 写的同步性 |
| 实现方式 | 版本链+快照读 | 数据库锁 |
| 适用场景 | SELECT查询 | UPDATE/DELETE |
| 并发度 | 高（读不阻塞） | 相对较低 |

### 锁类型总结

```
全局锁
  └─ FLUSH TABLES WITH READ LOCK

表级锁
  ├─ 表锁 (LOCK TABLES ... READ/WRITE)
  ├─ 元数据锁 (MDL)
  └─ 意向锁 (IS/IX)

行级锁 (InnoDB)
  ├─ 记录锁 (Record Lock)
  ├─ 间隙锁 (Gap Lock)
  ├─ 临键锁 (Next-Key Lock = Record + Gap)
  ├─ 插入意向锁 (Insert Intention Lock)
  └─ 自增锁 (AUTO-INC Lock)
```

### 最佳实践

1. **优先使用行锁**：粒度小，并发度高
2. **尽量缩短事务**：减少锁持有时间
3. **统一加锁顺序**：避免死锁
4. **合理使用索引**：避免行锁升级为表锁
5. **读多写少用乐观锁**：减少锁竞争
6. **写操作频繁用悲观锁**：保证数据一致性

---

## 🎓 学习建议

1. **按顺序运行测试**：从场景1到场景7，逐步深入
2. **观察日志输出**：理解锁的获取和释放时机
3. **修改参数实验**：调整sleep时间、并发数等
4. **查询系统表**：实时查看锁的状态
5. **模拟生产场景**：结合实际业务理解锁的应用

---

## 📚 扩展阅读

- MySQL官方文档：InnoDB Locking
- 《高性能MySQL》第一章：锁和事务
- 《MySQL技术内幕》第七章：锁机制

---

## ⚠️ 注意事项

1. **生产环境慎用**：这些测试会产生锁等待和死锁
2. **资源清理**：每个测试后自动清理数据
3. **超时设置**：建议设置合理的锁等待超时时间
   ```sql
   SET innodb_lock_wait_timeout = 5;
   ```
4. **事务隔离级别**：确保使用REPEATABLE READ测试间隙锁

Happy Learning! 🎉